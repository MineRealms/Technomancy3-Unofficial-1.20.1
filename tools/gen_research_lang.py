#!/usr/bin/env python3
"""Derive the S1-B research lang entries from the legacy language files.

The Chinese text is donated by the 1.12 fork (``reference/legacy-1.12/lang/zh_cn.json``),
whose 1.12 loader lower-cased every research key. Copying it programmatically instead of
retyping keeps the CJK bytes intact; the Windows console mangles CJK, so review the result
with ``--print`` (or by reading the file) rather than by eye in a terminal.

Every deviation from the legacy strings is applied here explicitly and listed in
``docs/VALIDATION.zh-CN.md``. Re-running the script is idempotent: it refuses to clobber a
key that a hand edit already changed unless ``--force`` is given.

Usage:
    python tools/gen_research_lang.py            # write assets/technom/lang/{en_us,zh_cn}.json
    python tools/gen_research_lang.py --check     # exit 1 if the lang files are out of date
    python tools/gen_research_lang.py --print     # dump what would be written, as UTF-8 JSON
"""

from __future__ import annotations

import argparse
import collections
import io
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LEGACY_EN = ROOT / "reference/legacy-1.12/lang/en_us.json"
LEGACY_ZH = ROOT / "reference/legacy-1.12/lang/zh_cn.json"
TARGET_EN = ROOT / "src/main/resources/assets/technom/lang/en_us.json"
TARGET_ZH = ROOT / "src/main/resources/assets/technom/lang/zh_cn.json"

# TC4R derives these two key shapes from the research key itself
# (ResearchKey.nameTranslationKey / descriptionTranslationKey), and a namespaced
# addon key serializes as "<namespace>:<KEY>", colon included.
NAME_KEY = "tc.research_name.technom:%s"
TEXT_KEY = "tc.research_text.technom:%s"
CATEGORY_KEY = "tc.research_category.technom:TECHNOMANCY"
# Page keys are free-form: they are whatever the page's "value" field says.
PAGE_KEY = "technom.research_page.%s.%d"

RESEARCH = ("TECHNOBASICS", "QUANTUMJARS", "DYNAMO", "CONDENSER")
# DYNAMO.3 is the node dynamo page and is out of S1-B scope.
PAGES = (("TECHNOBASICS", 1), ("QUANTUMJARS", 1), ("DYNAMO", 1), ("DYNAMO", 2),
         ("DYNAMO", 4), ("CONDENSER", 1))

CJK = r"[　-〿㐀-䶿一-鿿＀-￯]"


def load(path: pathlib.Path) -> "collections.OrderedDict[str, str]":
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=collections.OrderedDict)


def unwrap_cjk(text: str) -> str:
    """Drop the hand-inserted line-break spaces of the 1.7.10 fixed-width pages.

    Only spaces with a CJK glyph on both sides are removed, so spacing around Latin
    words and numbers survives untouched.
    """
    previous = None
    while previous != text:
        previous = text
        text = re.sub("(%s) +(%s)" % (CJK, CJK), r"\1\2", text)
    return text


def build() -> "tuple[dict[str, str], dict[str, str]]":
    legacy_en, legacy_zh = load(LEGACY_EN), load(LEGACY_ZH)
    english: "collections.OrderedDict[str, str]" = collections.OrderedDict()
    chinese: "collections.OrderedDict[str, str]" = collections.OrderedDict()

    english[CATEGORY_KEY] = legacy_en["tc.research_category.technomancy"]
    chinese[CATEGORY_KEY] = legacy_zh["tc.research_category.technomancy"]

    for key in RESEARCH:
        low = key.lower()
        english[NAME_KEY % key] = legacy_en["tc.research_name." + low]
        english[TEXT_KEY % key] = legacy_en["tc.research_text." + low]
        chinese[NAME_KEY % key] = legacy_zh["tc.research_name." + low]
        chinese[TEXT_KEY % key] = legacy_zh["tc.research_text." + low]

    for key, page in PAGES:
        source = "techno.research_page.%s.%d" % (key.lower(), page)
        english[PAGE_KEY % (key, page)] = legacy_en[source]
        chinese[PAGE_KEY % (key, page)] = unwrap_cjk(legacy_zh[source])

    # --- Deliberate deviations from the legacy text ---------------------------------
    # (1) The legacy zh CONDENSER page hard-codes "about 1,000,000 RF per Potentia".
    #     The cost is a config knob now (condenserCostQ, default 200,000 Q), so the
    #     fixed figure would be wrong. Strip the parenthetical before the RF rewrite
    #     below introduces full-width parentheses of its own.
    condenser = PAGE_KEY % ("CONDENSER", 1)
    chinese[condenser] = re.sub(r"[.。]?\s*[(（][^()（）]*[)）]\s*$",
                                "。", chinese[condenser])

    # (2) Thermal Expansion and CoFH RF are out of scope; the unit is Q (1 Q = 1 FE).
    dynamo1 = PAGE_KEY % ("DYNAMO", 1)
    english[dynamo1] = (english[dynamo1]
                        .replace("using Redstone Flux", "using Forge Energy")
                        .replace("turn it into Redstone Flux", "turn it into Forge Energy (Q)"))
    chinese[dynamo1] = (chinese[dynamo1]
                        .replace("产生RF的机器",
                                 "产生 Q（Forge Energy）的机器")
                        .replace("转化为RF", "转化为 Q"))
    english[condenser] = english[condenser].replace(
        "you can turn RF into some Potentia",
        "you can turn Forge Energy (Q) into some Potentia")
    chinese[condenser] = chinese[condenser].replace(
        "把RF能转化为",
        "把 Q（Forge Energy）转化为")

    # (3) The default redstone mode is decided on the dynamo branch (SPEC section 10
    #     item 3), so this branch must not claim one. DYNAMO.2 still lists all three
    #     switch items, so nothing is lost. DYNAMO.2 itself stays verbatim: "returns
    #     it to its original behaviour" names no default.
    english[dynamo1] = english[dynamo1].replace(
        " By default the Essentia Dynamo requires a redstone signal to run,"
        " but this can be changed.",
        " How the Essentia Dynamo answers a redstone signal can be reconfigured in place.")
    chinese[dynamo1] = chinese[dynamo1].replace(
        "神秘能源炉默认需要一个红石"
        "信号来进行工作，不过这个可"
        "以改变。",
        "源质发电机对红石信号的响应"
        "方式可以就地重新设定。")

    # (4) State where the Potentia cost actually comes from.
    english[condenser] += (" The energy cost of one Potentia is set by the condenserCostQ"
                           " config value (200,000 Q by default).")
    chinese[condenser] += ("每点 Potentia 的能量成本由"
                           "配置项 condenserCostQ 决定（默"
                           "认 200,000 Q）。")

    # (5) Name the upgrade as the player sees it: the legacy zh page calls the Potency
    #     Gem "能量宝石" while item.technom.potency_gem is
    #     "力量宝石".
    dynamo4 = PAGE_KEY % ("DYNAMO", 4)
    chinese[dynamo4] = chinese[dynamo4].replace("能量宝石",
                                                "力量宝石")

    for generated in (english, chinese):
        for key, value in generated.items():
            if not value.strip():
                raise SystemExit("generated an empty value for " + key)
    if set(english) != set(chinese):
        raise SystemExit("en/zh key sets differ: %s" % (set(english) ^ set(chinese)))
    return english, chinese


def merge(target: pathlib.Path, generated: "dict[str, str]", force: bool) -> str:
    existing = load(target)
    merged = collections.OrderedDict(existing)
    for key, value in generated.items():
        if key in existing and existing[key] != value and not force:
            raise SystemExit("%s: %s was hand-edited; re-run with --force to overwrite"
                             % (target.name, key))
        merged[key] = value
    return json.dumps(merged, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the lang files are not up to date")
    parser.add_argument("--print", dest="dump", action="store_true",
                        help="print the generated entries as UTF-8 JSON and exit")
    parser.add_argument("--force", action="store_true",
                        help="overwrite hand-edited values of generated keys")
    arguments = parser.parse_args()

    english, chinese = build()
    if arguments.dump:
        payload = {key: {"en": english[key], "zh": chinese[key]} for key in english}
        sys.stdout.buffer.write(
            (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
        return 0

    status = 0
    for target, generated in ((TARGET_EN, english), (TARGET_ZH, chinese)):
        rendered = merge(target, generated, arguments.force)
        current = io.open(target, encoding="utf-8").read()
        if arguments.check:
            if current != rendered:
                print("out of date: %s" % target.relative_to(ROOT))
                status = 1
            else:
                print("up to date: %s" % target.relative_to(ROOT))
            continue
        if current != rendered:
            io.open(target, "w", encoding="utf-8", newline="\n").write(rendered)
            print("wrote %d research keys into %s"
                  % (len(generated), target.relative_to(ROOT)))
        else:
            print("unchanged: %s" % target.relative_to(ROOT))
    return status


if __name__ == "__main__":
    raise SystemExit(main())
