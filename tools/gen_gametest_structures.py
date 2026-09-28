#!/usr/bin/env python3
"""Generate the dev-only GameTest structure templates of Technomancy.

The templates are vanilla structure NBT (gzip-compressed, DataVersion 3465 =
Minecraft 1.20.1) written by a small hand-rolled NBT encoder, so no game or
third-party library is needed. Output is byte-for-byte reproducible (gzip
mtime is fixed to 0).

    python tools/gen_gametest_structures.py          # (re)write the templates
    python tools/gen_gametest_structures.py --check  # fail if a file is stale

Templates live under data/technom/structures/gametest/ and are excluded from
the release JAR by build.gradle together with the gametest package.
"""

from __future__ import annotations

import argparse
import gzip
import struct
import sys
from pathlib import Path

DATA_VERSION_1_20_1 = 3465
STRUCTURE_DIR = (Path(__file__).resolve().parents[1] / "src" / "main" / "resources" /
                 "data" / "technom" / "structures" / "gametest")

# NBT tag ids used by structure templates.
TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Tag:
    """A typed NBT value; lists carry their element type explicitly."""

    def __init__(self, tag_id: int, value, element_id: int = TAG_END):
        self.tag_id = tag_id
        self.value = value
        self.element_id = element_id


def nbt_int(value: int) -> Tag:
    return Tag(TAG_INT, value)


def nbt_string(value: str) -> Tag:
    return Tag(TAG_STRING, value)


def nbt_list(element_id: int, items: list[Tag]) -> Tag:
    assert all(item.tag_id == element_id for item in items)
    # Like vanilla ListTag, an empty list is written with element type TAG_End.
    return Tag(TAG_LIST, items, element_id if items else TAG_END)


def nbt_compound(entries: dict[str, Tag]) -> Tag:
    return Tag(TAG_COMPOUND, entries)


def encode_name(name: str) -> bytes:
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def encode_payload(tag: Tag) -> bytes:
    if tag.tag_id == TAG_INT:
        return struct.pack(">i", tag.value)
    if tag.tag_id == TAG_STRING:
        return encode_name(tag.value)
    if tag.tag_id == TAG_LIST:
        return (bytes([tag.element_id]) + struct.pack(">i", len(tag.value)) +
                b"".join(encode_payload(item) for item in tag.value))
    if tag.tag_id == TAG_COMPOUND:
        body = b"".join(bytes([child.tag_id]) + encode_name(name) + encode_payload(child)
                        for name, child in tag.value.items())
        return body + bytes([TAG_END])
    raise ValueError(f"unsupported NBT tag {tag.tag_id}")


def encode_root(root: Tag) -> bytes:
    return bytes([TAG_COMPOUND]) + encode_name("") + encode_payload(root)


def box_template(size: tuple[int, int, int], block_at) -> Tag:
    """Structure covering every position of ``size``; ``block_at(x, y, z)`` names its block."""
    palette: list[str] = []
    blocks: list[Tag] = []
    sx, sy, sz = size
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                name = block_at(x, y, z)
                if name not in palette:
                    palette.append(name)
                blocks.append(nbt_compound({
                    "pos": nbt_list(TAG_INT, [nbt_int(x), nbt_int(y), nbt_int(z)]),
                    "state": nbt_int(palette.index(name)),
                }))
    return nbt_compound({
        "DataVersion": nbt_int(DATA_VERSION_1_20_1),
        "size": nbt_list(TAG_INT, [nbt_int(v) for v in size]),
        "palette": nbt_list(TAG_COMPOUND, [nbt_compound({"Name": nbt_string(n)}) for n in palette]),
        "blocks": nbt_list(TAG_COMPOUND, blocks),
        "entities": nbt_list(TAG_COMPOUND, []),
    })


# Template name (relative to data/technom/structures/) -> root tag.
TEMPLATES = {
    # Empty 5x5x5 test room: smooth-stone floor at y=0, air everywhere above,
    # so tests never depend on the terrain the GameTest server places them on.
    "gametest/empty_5x5x5": box_template(
        (5, 5, 5), lambda x, y, z: "minecraft:smooth_stone" if y == 0 else "minecraft:air"),
    # 7x5x9 room: the smallest that fits a facing node-fabricator pair (six
    # blocks apart, node three blocks along and one up) plus both 3x3x3 slabs.
    "gametest/empty_7x5x9": box_template(
        (7, 5, 9), lambda x, y, z: "minecraft:smooth_stone" if y == 0 else "minecraft:air"),
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify instead of writing")
    args = parser.parse_args()
    stale = []
    for name, root in TEMPLATES.items():
        target = STRUCTURE_DIR.parent / f"{name}.nbt"
        payload = encode_root(root)
        current = gzip.decompress(target.read_bytes()) if target.is_file() else None
        if current == payload:
            print(f"up to date: {target.name}")
            continue
        if args.check:
            stale.append(target)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(gzip.compress(payload, mtime=0))
        print(f"wrote {target}")
    for target in stale:
        print(f"stale: {target}", file=sys.stderr)
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
