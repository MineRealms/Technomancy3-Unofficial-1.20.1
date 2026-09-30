#!/usr/bin/env python3
"""Static link-check of a mod's references to the JEI API.

For every class in the target jar that touches `mezz/jei`, this resolves each
referenced class / method / field against one or more JEI versions and reports the
references that a given JEI version cannot satisfy.

This is what tells us whether a JEI version mismatch is "two missing symbols" (and
therefore shimmable) or "the whole integration moved" (and therefore not).

Usage:
  python tools/jei_link_check.py <jar> <jei-extracted-dir> [<jar2> ...]
"""
import os
import struct
import subprocess
import sys
import zipfile
from collections import defaultdict

# ---------------------------------------------------------------- class parsing

def parse_class(data: bytes):
    """Return (name, super, interfaces, methods, fields) from class-file bytes."""
    off = 8
    cp_count = struct.unpack_from(">H", data, off)[0]
    off += 2
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        tag = data[off]
        off += 1
        if tag == 1:  # Utf8
            ln = struct.unpack_from(">H", data, off)[0]
            off += 2
            cp[i] = data[off:off + ln].decode("utf-8", "replace")
            off += ln
        elif tag in (3, 4):  # Integer, Float
            off += 4
        elif tag in (5, 6):  # Long, Double
            off += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            # A Class entry only stores the index of the Utf8 that names it; keep that so
            # cls() can resolve it once the whole pool has been walked.
            if tag == 7:
                cp[i] = struct.unpack_from(">H", data, off)[0]
            off += 2
        elif tag in (9, 10, 11, 12, 17, 18):  # refs, NameAndType, Dynamic
            off += 4
        elif tag == 15:  # MethodHandle
            off += 3
        else:
            raise ValueError(f"bad constant tag {tag}")
        i += 1

    def utf(idx):
        return cp[idx]

    def cls(idx):
        return cp[cp[idx]]

    off += 2  # access flags
    this = cls(struct.unpack_from(">H", data, off)[0]); off += 2
    sup = cls(struct.unpack_from(">H", data, off)[0]); off += 2
    n = struct.unpack_from(">H", data, off)[0]; off += 2
    ifaces = [cls(struct.unpack_from(">H", data, off + 2 * k)[0]) for k in range(n)]
    off += 2 * n

    def members():
        nonlocal off
        cnt = struct.unpack_from(">H", data, off)[0]; off += 2
        out = []
        for _ in range(cnt):
            off += 2  # access
            name = utf(struct.unpack_from(">H", data, off)[0]); off += 2
            desc = utf(struct.unpack_from(">H", data, off)[0]); off += 2
            acount = struct.unpack_from(">H", data, off)[0]; off += 2
            for _ in range(acount):
                alen = struct.unpack_from(">I", data, off + 2)[0]
                off += 6 + alen
            out.append((name, desc))
        return out

    fields = members()
    methods = members()
    return this, sup, ifaces, methods, fields


def parse_refs(data: bytes):
    """Return (classrefs, methodrefs, fieldrefs) as (owner, name, desc) tuples."""
    off = 8
    cp_count = struct.unpack_from(">H", data, off)[0]
    off += 2
    # Two passes are needed because a Class entry names a Utf8 and a Methodref points
    # at a Class plus a NameAndType, both of which may appear later in the pool.
    tags = []
    utf = {}
    i = 1
    while i < cp_count:
        tag = data[off]; off += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, off)[0]; off += 2
            utf[i] = data[off:off + ln].decode("utf-8", "replace"); off += ln
            tags.append((i, tag, None))
        elif tag in (3, 4):
            tags.append((i, tag, None)); off += 4
        elif tag in (5, 6):
            tags.append((i, tag, None)); off += 8; i += 1
        elif tag == 7:
            tags.append((i, tag, struct.unpack_from(">H", data, off)[0])); off += 2
        elif tag in (8, 16, 19, 20):
            tags.append((i, tag, None)); off += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            tags.append((i, tag, struct.unpack_from(">HH", data, off))); off += 4
        elif tag == 15:
            tags.append((i, tag, None)); off += 3
        else:
            raise ValueError(f"bad constant tag {tag}")
        i += 1

    classes, methods, fields = set(), set(), set()
    # A Methodref/Fieldref names its owner through a Class entry, so resolve those
    # first: class_index -> internal name.
    cls_name = {idx: utf.get(payload) for idx, tag, payload in tags if tag == 7}
    for idx, tag, payload in tags:
        if tag == 7:
            name = cls_name.get(idx)
            if name and name.startswith("mezz/jei/"):
                classes.add(name)
        elif tag in (9, 10, 11):
            ci, nti = payload
            owner = cls_name.get(ci)
            if not owner or not owner.startswith("mezz/jei/"):
                continue
            for j, t2, p2 in tags:
                if j == nti and t2 == 12:
                    name = utf.get(p2[0])
                    desc = utf.get(p2[1])
                    if name is None or desc is None:
                        break
                    (fields if tag == 9 else methods).add((owner, name, desc))
                    break
    return classes, methods, fields


# ---------------------------------------------------------------- JEI index

class JeiIndex:
    def __init__(self, root: str):
        self.root = root
        self.methods = defaultdict(set)
        self.fields = defaultdict(set)
        self.supers = {}
        self.present = set()
        self._load()

    def _load(self):
        for dirpath, _, files in os.walk(self.root):
            for f in files:
                if not f.endswith(".class"):
                    continue
                path = os.path.join(dirpath, f)
                rel = os.path.relpath(path, self.root).replace(os.sep, "/")[:-6]
                self.present.add(rel)
                try:
                    with open(path, "rb") as fh:
                        data = fh.read()
                    name, sup, ifaces, ms, fs = parse_class(data)
                except Exception:
                    continue
                self.methods[name].update(ms)
                self.fields[name].update(fs)
                self.supers[name] = [sup] + ifaces

    def has_class(self, name):
        return name in self.present

    def _walk(self, name, seen=None):
        seen = seen if seen is not None else set()
        if name in seen or name not in self.supers:
            return
        seen.add(name)
        yield name
        for parent in self.supers[name]:
            if parent:
                yield from self._walk(parent, seen)

    def has_member(self, owner, name, desc, is_field=False):
        table = self.fields if is_field else self.methods
        for cls in self._walk(owner):
            if (name, desc) in table.get(cls, ()):
                return True
        return False

    def has_member_by_name(self, owner, name, is_field=False):
        table = self.fields if is_field else self.methods
        for cls in self._walk(owner):
            if any(n == name for n, _ in table.get(cls, ())):
                return True
        return False


# ---------------------------------------------------------------- main

def iter_classes(target: str):
    """Yield (display_name, bytes) for every class in a jar or a classes directory."""
    if os.path.isdir(target):
        for dirpath, _, files in os.walk(target):
            for f in files:
                if f.endswith(".class"):
                    path = os.path.join(dirpath, f)
                    rel = os.path.relpath(path, target).replace(os.sep, "/")
                    with open(path, "rb") as fh:
                        yield rel, fh.read()
    else:
        with zipfile.ZipFile(target) as z:
            for n in z.namelist():
                if n.endswith(".class"):
                    yield n, z.read(n)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    jar, jei_root = argv[0], argv[1]

    idx = JeiIndex(jei_root)
    print(f"JEI tree {jei_root}: {len(idx.present)} classes\n")

    missing_class = set()
    missing_member = set()
    moved_member = set()
    checked = 0

    for n, data in iter_classes(jar):
        try:
            classes, methods, fields = parse_refs(data)
        except Exception:
            continue
        jei_classes = {c for c in classes if c.startswith("mezz/jei/")}
        jei_methods = {m for m in methods if m[0].startswith("mezz/jei/")}
        jei_fields = {f for f in fields if f[0].startswith("mezz/jei/")}
        if not (jei_classes or jei_methods or jei_fields):
            continue
        checked += 1
        for c in jei_classes:
            if not idx.has_class(c):
                missing_class.add((n, c))
        for owner, name, desc in jei_methods:
            if not idx.has_class(owner):
                missing_class.add((n, owner))
            elif not idx.has_member(owner, name, desc):
                if idx.has_member_by_name(owner, name):
                    moved_member.add((n, owner, name, desc))
                else:
                    missing_member.add((n, owner, name, desc))
        for owner, name, desc in jei_fields:
            if not idx.has_class(owner):
                missing_class.add((n, owner))
            elif not idx.has_member(owner, name, desc, is_field=True):
                missing_member.add((n, owner, name, desc))

    print(f"scanned {checked} class(es) in {os.path.basename(jar)} that reference mezz/jei\n")

    def dump(title, rows, fmt):
        print(f"--- {title}: {len(rows)} ---")
        for r in sorted(rows):
            print("   " + fmt(r))
        print()

    dump("MISSING CLASS (NoClassDefFoundError)", missing_class,
         lambda r: f"{r[0]}  ->  {r[1]}")
    dump("MISSING MEMBER (NoSuchMethodError / NoSuchFieldError)", missing_member,
         lambda r: f"{r[0]}  ->  {r[1]}.{r[2]}{r[3]}")
    dump("MEMBER NAME EXISTS BUT DESCRIPTOR DIFFERS (signature drift)", moved_member,
         lambda r: f"{r[0]}  ->  {r[1]}.{r[2]}{r[3]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
