#!/usr/bin/env python3
"""Bulk-import the legacy Technomancy resources into the 1.20.1 resource tree.

Base is the 1.12 fork (../Technomancy-2), which already lowercased most file
names; the 1.7.10 master holds the same art under camelCase names and contains
no file the 1.12 tree lacks (verified case-insensitively), so it is not a
second source. Everything here is mechanical:

  * textures/blocks -> textures/block and textures/items -> textures/item
    (the 1.13 "flattening"), with the matching rewrites inside model JSON.
  * every file name lowercased, because 1.20 ResourceLocations reject
    uppercase.
  * three vanilla textures that were renamed in 1.13 remapped by hand.
  * .lang -> .json, zh_CN -> zh_cn, and lang KEYS lowercased. The 1.12 pair
    disagrees on case (en was lowercased, zh was not), so only 78 of the 130
    Chinese entries match an English key exactly; normalising the case lines
    up all 130 while keeping the larger 149-key English set.
  * a sprite whose size is not a whole number of frames and which carries no
    .mcmeta cannot be stitched into an atlas, so it is not a block/item
    sprite at all and is filed under textures/entity/ instead.
  * legacy renderer skins move from textures/models/ to textures/entity/, because
    TC4R's atlas additions stitch textures/models/ of every namespace into the
    shared block atlas.

Not mechanical, and therefore NOT imported into the resource path: the 1.12
blockstates use the Forge "forge_marker" format, which was removed in 1.13.
They are kept verbatim under reference/legacy-1.12/blockstates/ as the record
of which variants each block had, to be rewritten per block at registration.

    python tools/import_legacy_assets.py [--check]
"""

from __future__ import annotations

import argparse
import re
import shutil
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / "Technomancy-2" / "src" / "main" / "resources"
SOURCE_ASSETS = SOURCE / "assets" / "technom"
TARGET_ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "technom"
REFERENCE = ROOT / "reference" / "legacy-1.12"

# 1.13 flattening of the texture roots.
TEXTURE_DIRS = {"blocks": "block", "items": "item"}

# Where renderer skins go. NOT textures/models/, even though that is where 1.7.10 kept
# them: TC4R ships assets/minecraft/atlases/blocks.json adding
# {"type": "directory", "source": "models", "prefix": "models/"} to the vanilla block
# atlas, and an atlas directory source scans every namespace - so with TC4R installed,
# anything under textures/models/ is stitched into the shared block atlas whether we want
# it or not. That cost us real quality: our 1x1 sphere.png dropped the whole atlas to mip
# level 0, i.e. no mipmapping for every block in the game. Nothing stitches
# textures/entity/, and a renderer binds its texture by ResourceLocation anyway, so it
# needs no sprite.
RENDERER_DIR = "entity"

# Vanilla textures the flattening renamed; the legacy models still use the old ids.
VANILLA_RENAMES = {
    "blocks/dirt": "block/dirt",
    "blocks/cobblestone_mossy": "block/mossy_cobblestone",
    "blocks/anvil_base": "block/anvil",
    "items/bucket_water": "item/water_bucket",
}

TEXTURE_REF = re.compile(r'"(technom:)?(blocks|items)/([A-Za-z0-9_./-]+)"')


def rewrite_texture_refs(text: str) -> str:
    """Flatten and lowercase the texture ids inside a model/blockstate JSON."""

    def replace(match: re.Match[str]) -> str:
        namespace, root, path = match.groups()
        if not namespace:
            legacy = f"{root}/{path}"
            return '"%s"' % VANILLA_RENAMES.get(legacy, f"{TEXTURE_DIRS[root]}/{path}".lower())
        return '"technom:%s/%s"' % (TEXTURE_DIRS[root], path.lower())

    return TEXTURE_REF.sub(replace, text)


def lang_to_json(text: str) -> tuple[str, list[str]]:
    """Convert a 1.12 key=value .lang file into a 1.20 lang JSON object.

    Keys are lowercased. The 1.12 files disagree on case — en_us was
    lowercased at some point and zh_CN was not — so without this only 78 of
    the 130 Chinese entries share a key with an English one, and the rest
    would be dead weight. No key collides once lowercased.
    """
    entries: dict[str, str] = {}
    duplicates: list[str] = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip().lower()
        if key in entries:
            duplicates.append(key)
            continue
        entries[key] = value
    body = ",\n".join('  %s: %s' % (dump(k), dump(v)) for k, v in entries.items())
    return "{\n%s\n}\n" % body, duplicates


def png_size(payload: bytes) -> tuple[int, int]:
    """Width and height from the IHDR chunk."""
    return struct.unpack(">II", payload[16:24])


def stitchable(payload: bytes, has_mcmeta: bool) -> bool:
    """
    Whether a sprite can go into a block/item atlas.

    A sprite without animation metadata is one frame of min(w, h) square, so
    both dimensions must be whole multiples of it. Legacy trees contain art
    that was bound directly by a renderer and never stitched, where any size
    was fine; in 1.20 such a file fails to load and logs an error on every
    resource reload.
    """
    if has_mcmeta:
        return True
    width, height = png_size(payload)
    frame = min(width, height)
    return width % frame == 0 and height % frame == 0


def dump(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return '"%s"' % escaped


class Importer:
    def __init__(self, check: bool) -> None:
        self.check = check
        self.stale: list[Path] = []
        self.written = 0
        self.notes: list[str] = []

    def put(self, target: Path, payload: bytes) -> None:
        current = target.read_bytes() if target.is_file() else None
        if current == payload:
            return
        if self.check:
            self.stale.append(target)
            return
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        self.written += 1

    def run(self) -> int:
        if not SOURCE_ASSETS.is_dir():
            print(f"missing source tree: {SOURCE_ASSETS}", file=sys.stderr)
            return 2

        # Textures, including the .png.mcmeta animation descriptors beside them.
        for path in sorted((SOURCE_ASSETS / "textures").rglob("*")):
            if not path.is_file():
                continue
            parts = list(path.relative_to(SOURCE_ASSETS / "textures").parts)
            atlas_root = parts[0] in TEXTURE_DIRS
            if parts[0] == "models":
                parts[0] = RENDERER_DIR
            else:
                parts[0] = TEXTURE_DIRS.get(parts[0], parts[0])
            relative = Path(*[part.lower() for part in parts])
            payload = path.read_bytes()
            if atlas_root and path.suffix == ".png" and not stitchable(
                    payload, path.with_suffix(".png.mcmeta").is_file()):
                width, height = png_size(payload)
                relative = Path(RENDERER_DIR) / relative.name
                self.notes.append(
                    f"{path.name} is {width}x{height} with no animation metadata, so it cannot be "
                    f"stitched into an atlas; filed as textures/{relative.as_posix()}")
            self.put(TARGET_ASSETS / "textures" / relative, payload)

        # Block and item models: valid 1.20 JSON once the texture ids are flattened.
        for path in sorted((SOURCE_ASSETS / "models").rglob("*.json")):
            relative = path.relative_to(SOURCE_ASSETS / "models")
            text = rewrite_texture_refs(path.read_text(encoding="utf-8"))
            target = TARGET_ASSETS / "models" / Path(*[part.lower() for part in relative.parts])
            self.put(target, text.encode("utf-8"))

        # Language files: format change only, keys are left exactly as they were.
        # These land in reference/, NOT in assets/: every legacy key uses the old
        # "techno:" prefix and the 1.7.10 "tile.X.name" shape, so none of them is a
        # 1.20 translation key. They are the source text for hand-authored lang
        # files, which own assets/technom/lang and must not be overwritten here.
        for path in sorted((SOURCE_ASSETS / "lang").glob("*.lang")):
            payload, duplicates = lang_to_json(path.read_text(encoding="utf-8"))
            target = REFERENCE / "lang" / f"{path.stem.lower()}.json"
            self.put(target, payload.encode("utf-8"))
            if duplicates:
                self.notes.append(f"{target.name}: dropped duplicate keys {sorted(set(duplicates))}")

        # The mod logo, for mods.toml logoFile once someone wires it up.
        logo = SOURCE_ASSETS / "Technomancy.png"
        if logo.is_file():
            self.put(ROOT / "src" / "main" / "resources" / "technomancy.png", logo.read_bytes())

        # Unusable in 1.20, kept verbatim as reference rather than shipped broken.
        for path in sorted((SOURCE_ASSETS / "blockstates").glob("*.json")):
            self.put(REFERENCE / "blockstates" / path.name.lower(), path.read_bytes())

        for note in self.notes:
            print(f"note: {note}")
        if self.stale:
            for path in self.stale:
                print(f"stale: {path.relative_to(ROOT)}", file=sys.stderr)
            return 1
        print(f"imported {self.written} files" if self.written else "up to date")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify instead of writing")
    return Importer(parser.parse_args().check).run()


if __name__ == "__main__":
    sys.exit(main())
