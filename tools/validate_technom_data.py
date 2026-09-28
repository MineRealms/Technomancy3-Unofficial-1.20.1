#!/usr/bin/env python3
"""Validate the Technomancy data pack against the TC4R schemas and the real registries.

Three classes of mistake kill data-pack content without failing a build:

* a JSON syntax error, which drops one file;
* a misspelled *field*, which for a research or object-aspect file raises inside the loader and
  drops the entire file, and for a recipe is silently ignored by the MapCodec (so a typo'd
  ``research`` key quietly removes the research gate);
* a misspelled *id*, which logs one line and drops the recipe, or -- for object aspects -- throws
  and takes the file with it.

This script catches all three before the game runs. The field whitelists below are transcribed
from the TC4R 0.1.0-20721 codecs and loaders, each with the source that defines it. Ids are
resolved against an inventory dumped by ``DataPackGameTests#s1bWritesRegistryInventoryForTooling``
during ``runGameTestServer``; without ``--inventory`` the id checks are reported as SKIPPED rather
than silently passing.

An id that the running server does not have is an error *unless* the same file carries a
``forge:item_exists`` condition for it, which is how the essentia dynamo and energy condenser
content stays dormant until those blocks are registered.

Usage:
    python tools/validate_technom_data.py
    python tools/validate_technom_data.py --inventory run-gametest/technom-data-inventory.json
"""

from __future__ import annotations

import argparse
import io
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAMESPACE = "technom"
DATA = ROOT / "src/main/resources/data" / NAMESPACE
ASSETS = ROOT / "src/main/resources/assets" / NAMESPACE
LANG_FILES = ("en_us.json", "zh_cn.json")

CONDITION_MEMBERS = ("conditions", "forge:conditions")

# RecipeManager's Forge patch reads the array under "conditions"; the TC4R catalogs read
# ConditionPlatform.MEMBER == "forge:conditions". Both are stripped before schema checking.
RECIPE_CONDITION_MEMBER = "conditions"
CATALOG_CONDITION_MEMBER = "forge:conditions"

# --- Field whitelists ---------------------------------------------------------------------------
# thaumcraft:arcane_shaped   ArcaneRecipe.Serializer.SERIALIZED_CODEC
# thaumcraft:arcane_shapeless ShapelessArcaneRecipe.Serializer.SERIALIZED_CODEC
# thaumcraft:crucible        CrucibleRecipe.Serializer.SERIALIZED_CODEC
# thaumcraft:infusion        InfusionRecipe.Serializer.SERIALIZED_CODEC
RECIPE_SCHEMA = {
    "thaumcraft:arcane_shaped": {
        "required": {"pattern", "key", "result", "vis"},
        "optional": {"research", "retain"},
    },
    "thaumcraft:arcane_shapeless": {
        "required": {"ingredients", "result", "vis"},
        "optional": {"research", "retain"},
    },
    "thaumcraft:crucible": {
        "required": {"catalyst", "result", "aspects"},
        "optional": {"research", "catalyst_aspects"},
    },
    "thaumcraft:infusion": {
        "required": {"central", "components", "instability", "aspects"},
        "optional": {"research", "result", "input_patch", "input_transformation"},
    },
    "minecraft:crafting_shaped": {
        "required": {"pattern", "key", "result"},
        "optional": {"group", "category", "show_notification"},
    },
    "minecraft:crafting_shapeless": {
        "required": {"ingredients", "result"},
        "optional": {"group", "category"},
    },
    # SimpleCookingSerializer. Forge patches it to accept an object "result" with a "count",
    # which is what the purified ores' rising yields per stage need.
    "minecraft:smelting": {
        "required": {"ingredient", "result"},
        "optional": {"group", "category", "experience", "cookingtime"},
    },
    # Botania's Mana Infusion (botania:mana_infusion), used by the S3 Botania module while the
    # optional mod is present. "catalyst" is optional; "group" mirrors the vanilla field.
    "botania:mana_infusion": {
        "required": {"input", "mana", "output"},
        "optional": {"catalyst", "group"},
    },
}

# VisChannel: the six primal channels are the only legal "vis" keys.
VIS_CHANNELS = {"aer", "terra", "ignis", "aqua", "ordo", "perditio"}

# ResearchCatalog.readFile / ENTRY_FIELDS / validatePageShape / validateIconShape
RESEARCH_ROOT_FIELDS = {"categories", "entries", "selector_exclusions"}
RESEARCH_CATEGORY_FIELDS = {"key", "icon", "background", "sort_order"}
RESEARCH_ENTRY_FIELDS = {
    "key", "forge:conditions", "category", "aspects", "display_column", "display_row",
    "complexity", "icon", "flags", "labels", "parents", "hidden_parents",
    "prerequisite_selectors", "siblings", "object_triggers", "entity_triggers",
    "aspect_triggers", "warp", "pages",
}
RESEARCH_ENTRY_REQUIRED = {"key", "category", "aspects", "display_column", "display_row",
                           "complexity", "icon", "pages"}
RESEARCH_PAGE_FIELDS = {"type", "value", "prerequisite", "aspects", "compound_blueprint",
                        "recipe_link_policy", "recipe_ids", "external_link"}
RESEARCH_ICON_FIELDS = {"TEXTURE": {"type", "texture"}, "STACK": {"type", "stack"},
                        "PRIMARY_ASPECT": {"type"}}
RESEARCH_FLAGS = {"SPECIAL", "STUB", "LOST", "CONCEALED", "HIDDEN", "VIRTUAL", "AUTO_UNLOCK",
                  "ROUND", "SECONDARY"}
# ResearchPageDefinition.Type / RecipeLinkPolicy
PAGE_TYPES = {"TEXT", "TEXT_CONCEALED", "LORE", "EXTERNAL_LINK", "CRUCIBLE_CRAFTING",
              "ARCANE_CRAFTING", "ASPECTS", "NORMAL_CRAFTING", "PROCESS_CRAFTING",
              "INFUSION_CRAFTING", "COMPOUND_CRAFTING", "INFUSION_ENCHANTMENT", "SMELTING",
              "SCEPTRE_CRAFTING"}
RECIPE_PAGE_TYPES = {"CRUCIBLE_CRAFTING", "ARCANE_CRAFTING", "NORMAL_CRAFTING",
                     "PROCESS_CRAFTING", "INFUSION_CRAFTING", "INFUSION_ENCHANTMENT", "SMELTING"}
LINK_POLICIES = {"DISABLED", "COMPLETED_SINGLE", "COMPLETED_CRUCIBLE_VARIANTS"}
# ResearchEntryDefinition clamps complexity into 1..3; anything outside is silently rewritten.
COMPLEXITY_RANGE = (1, 3)

# ObjectAspectCatalog.SYNCHRONIZED_ROOT_FIELDS minus the fields readFile rejects in game data.
ASPECT_ROOT_FIELDS = {"direct", "tags", "complex", "derived", "edits", "scan_groups",
                      "base_overrides", "wand_rules", "potion_rules", "potion_effect_magic",
                      "potion_effects", "equipment_rules", "enchantment_magic", "enchantments",
                      "culling", "dynamic_rules", "forge:conditions"}
ASPECT_FORBIDDEN_ROOT = {"resolved_counts", "runtime_entries", "post_inference_edits"}

RESOURCE_LOCATION = re.compile(r"^(?:([a-z0-9_.-]+):)?([a-z0-9_.\-/]+)$")
# ResearchKey.validNamespace / validPath: the path is case-preserving.
RESEARCH_KEY = re.compile(r"^(?:([a-z0-9_.-]+):)?([A-Za-z0-9_.\-/]+)$")
MAX_INSTABILITY_HINT = 100


class Report:
    def __init__(self) -> None:
        self.errors: "list[str]" = []
        self.warnings: "list[str]" = []
        self.skipped: "list[str]" = []
        self.checks = 0

    def error(self, where: str, message: str) -> None:
        self.errors.append("%s: %s" % (where, message))

    def warn(self, where: str, message: str) -> None:
        self.warnings.append("%s: %s" % (where, message))

    def skip(self, message: str) -> None:
        self.skipped.append(message)

    def ok(self) -> None:
        self.checks += 1


def rel(path: pathlib.Path) -> str:
    try:
        return str(path.relative_to(ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def only(report: Report, where: str, obj: dict, allowed: "set[str]",
         required: "set[str]" = frozenset()) -> None:
    for field in obj:
        if field not in allowed:
            report.error(where, "unknown field %r (allowed: %s)"
                         % (field, ", ".join(sorted(allowed))))
    for field in required:
        if field not in obj:
            report.error(where, "missing required field %r" % field)
    report.ok()


def check_resource_location(report: Report, where: str, value: object, what: str) -> bool:
    if not isinstance(value, str) or not RESOURCE_LOCATION.match(value):
        report.error(where, "%s %r is not a valid resource location "
                             "(paths are lower-case [a-z0-9_.-/])" % (what, value))
        return False
    report.ok()
    return True


def condition_items(node: object, found: "set[str]") -> None:
    """Collect every item id any forge:item_exists in this tree tests for."""
    if isinstance(node, list):
        for element in node:
            condition_items(element, found)
        return
    if not isinstance(node, dict):
        return
    if node.get("type") == "forge:item_exists" and isinstance(node.get("item"), str):
        found.add(node["item"])
    for key in ("values", "value"):
        if key in node:
            condition_items(node[key], found)


class Refs:
    def __init__(self) -> None:
        self.items: "list[tuple[str, str]]" = []          # (id, where)
        self.tags: "list[tuple[str, str]]" = []
        self.ingredient_types: "list[tuple[str, str]]" = []
        self.aspects: "list[tuple[str, str]]" = []
        self.recipe_ids: "list[tuple[str, str]]" = []
        self.lang_keys: "list[tuple[str, str]]" = []
        self.textures: "list[tuple[str, str]]" = []
        self.research_keys: "set[str]" = set()
        self.research_parents: "list[tuple[str, str]]" = []
        self.categories: "set[str]" = set()
        self.deferred: "dict[str, set[str]]" = {}          # file -> gated item ids


def read_ingredient(report: Report, refs: Refs, where: str, node: object) -> None:
    if isinstance(node, list):
        if not node:
            report.error(where, "ingredient list is empty")
        for index, element in enumerate(node):
            read_ingredient(report, refs, "%s[%d]" % (where, index), element)
        return
    if not isinstance(node, dict):
        report.error(where, "ingredient must be an object or a list, got %r" % type(node).__name__)
        return
    if "item" in node:
        if check_resource_location(report, where, node["item"], "item"):
            refs.items.append((node["item"], where))
    elif "tag" in node:
        if check_resource_location(report, where, node["tag"], "tag"):
            refs.tags.append((node["tag"], where))
    elif "type" in node:
        if check_resource_location(report, where, node["type"], "ingredient type"):
            refs.ingredient_types.append((node["type"], where))
    else:
        report.error(where, "ingredient has neither item, tag nor type: %s" % sorted(node))
    report.ok()


def read_result(report: Report, refs: Refs, where: str, node: object) -> None:
    if not isinstance(node, dict):
        report.error(where, "result must be an object")
        return
    # RecipeValuePlatform.ITEM_RESULT_KEYS
    only(report, where, node, {"id", "item", "count", "components", "nbt"})
    identifier = node.get("id", node.get("item"))
    if identifier is None:
        report.error(where, "result declares neither id nor item")
    elif check_resource_location(report, where, identifier, "result item"):
        refs.items.append((identifier, where))
    count = node.get("count", 1)
    if not isinstance(count, int) or not 1 <= count <= 99:
        report.error(where, "result count %r is outside 1..99 "
                            "(ForgeRecipeItemCodec rejects it)" % count)


def read_aspect_map(report: Report, refs: Refs, where: str, node: object,
                    primal_only: bool = False) -> None:
    if not isinstance(node, dict):
        report.error(where, "aspect map must be an object")
        return
    for aspect, amount in node.items():
        if primal_only:
            if aspect not in VIS_CHANNELS:
                report.error(where, "vis channel %r is not one of the six primals (%s)"
                             % (aspect, ", ".join(sorted(VIS_CHANNELS))))
                continue
        else:
            refs.aspects.append((aspect, where))
        if not isinstance(amount, int) or amount < 0:
            report.error(where, "aspect %s has amount %r" % (aspect, amount))
    report.ok()


def check_recipe(report: Report, refs: Refs, path: pathlib.Path, doc: object) -> None:
    where = rel(path)
    if not isinstance(doc, dict):
        report.error(where, "recipe root must be an object")
        return
    gated: "set[str]" = set()
    condition_items(doc.get(RECIPE_CONDITION_MEMBER, []), gated)
    if gated:
        refs.deferred.setdefault(where, set()).update(gated)
    if CATALOG_CONDITION_MEMBER in doc:
        report.error(where, "recipes are gated with %r, not %r (RecipeManager's Forge patch)"
                     % (RECIPE_CONDITION_MEMBER, CATALOG_CONDITION_MEMBER))

    recipe_type = doc.get("type")
    if recipe_type not in RECIPE_SCHEMA:
        report.error(where, "unsupported recipe type %r; TC4R 20721 registers only %s"
                     % (recipe_type, ", ".join(sorted(
                         t for t in RECIPE_SCHEMA if t.startswith("thaumcraft:")))))
        return
    schema = RECIPE_SCHEMA[recipe_type]
    allowed = schema["required"] | schema["optional"] | {"type", RECIPE_CONDITION_MEMBER}
    only(report, where, doc, allowed, schema["required"])

    recipe_id = "%s:%s" % (NAMESPACE, path.relative_to(DATA / "recipes")
                           .with_suffix("").as_posix())
    refs.recipe_ids.append((recipe_id, where))

    if "research" in doc:
        key = doc["research"]
        if not isinstance(key, str) or not RESEARCH_KEY.match(key):
            report.error(where, "research key %r is invalid" % key)
        else:
            refs.research_parents.append((key, where + " research"))

    if "pattern" in doc and "key" in doc:
        pattern, keys = doc["pattern"], doc["key"]
        if not isinstance(pattern, list) or not pattern or not all(
                isinstance(row, str) for row in pattern):
            report.error(where, "pattern must be a non-empty array of strings")
        elif len({len(row) for row in pattern}) != 1:
            report.error(where, "pattern rows have differing widths: %r" % pattern)
        else:
            used = {character for row in pattern for character in row if character != " "}
            declared = set(keys) if isinstance(keys, dict) else set()
            for symbol in sorted(used - declared):
                report.error(where, "pattern uses %r but key does not declare it" % symbol)
            for symbol in sorted(declared - used):
                report.error(where, "key declares %r but the pattern never uses it" % symbol)
            report.ok()
        if isinstance(keys, dict):
            for symbol, ingredient in keys.items():
                if len(symbol) != 1:
                    report.error(where, "key symbol %r must be a single character" % symbol)
                read_ingredient(report, refs, "%s key[%s]" % (where, symbol), ingredient)
    if "ingredients" in doc:
        read_ingredient(report, refs, where + " ingredients", doc["ingredients"])
    if "input" in doc:
        read_ingredient(report, refs, where + " input", doc["input"])
    if "catalyst" in doc:
        read_ingredient(report, refs, where + " catalyst", doc["catalyst"])
    if "central" in doc:
        read_ingredient(report, refs, where + " central", doc["central"])
    if "components" in doc:
        components = doc["components"]
        if not isinstance(components, list) or not components:
            report.error(where, "components must be a non-empty array")
        else:
            for index, component in enumerate(components):
                if isinstance(component, list):
                    report.error(where, "component %d is a candidate list; the 1.7.10 original"
                                        " collapsed distinct required items into one"
                                        " Ingredient.fromStacks, turning \"all of these\" into"
                                        " \"any one of these\"" % index)
                read_ingredient(report, refs, "%s components[%d]" % (where, index), component)
    if "result" in doc:
        read_result(report, refs, where + " result", doc["result"])
    if "output" in doc:
        read_result(report, refs, where + " output", doc["output"])
    if "mana" in doc:
        mana = doc["mana"]
        if not isinstance(mana, int) or mana < 0:
            report.error(where, "mana %r must be a non-negative integer" % mana)
    if "vis" in doc:
        read_aspect_map(report, refs, where + " vis", doc["vis"], primal_only=True)
    if "aspects" in doc:
        read_aspect_map(report, refs, where + " aspects", doc["aspects"])
    if "instability" in doc:
        instability = doc["instability"]
        if not isinstance(instability, int) or instability < 0:
            report.error(where, "instability %r must be a non-negative integer" % instability)
        elif instability > MAX_INSTABILITY_HINT:
            report.warn(where, "instability %d is unusually high" % instability)
    if "retain" in doc and not (isinstance(doc["retain"], list)
                                and all(isinstance(slot, int) and 0 <= slot <= 8
                                        for slot in doc["retain"])):
        report.error(where, "retain must be an array of slot indices 0..8")


def serialized_research_key(key: str) -> str:
    namespace, _, path = key.rpartition(":")
    return path if namespace in ("", "thaumcraft") else key


def check_research(report: Report, refs: Refs, path: pathlib.Path, doc: object) -> None:
    where = rel(path)
    if not isinstance(doc, dict):
        report.error(where, "research root must be an object")
        return
    gated: "set[str]" = set()
    condition_items(doc.get(CATALOG_CONDITION_MEMBER, []), gated)
    only(report, where, doc, RESEARCH_ROOT_FIELDS | {CATALOG_CONDITION_MEMBER})

    for category in doc.get("categories", []):
        place = "%s category %s" % (where, category.get("key"))
        only(report, place, category, RESEARCH_CATEGORY_FIELDS, {"key"})
        key = category.get("key")
        if isinstance(key, str) and RESEARCH_KEY.match(key):
            refs.categories.add(key)
            refs.lang_keys.append(("tc.research_category." + serialized_research_key(key), place))
        else:
            report.error(place, "category key %r is invalid" % key)
        for field in ("icon", "background"):
            if field in category and check_resource_location(
                    report, place, category[field], field):
                refs.textures.append((category[field], place))

    for entry in doc.get("entries", []):
        key = entry.get("key")
        place = "%s entry %s" % (where, key)
        only(report, place, entry, RESEARCH_ENTRY_FIELDS, RESEARCH_ENTRY_REQUIRED)
        entry_gated = set(gated)
        condition_items(entry.get(CATALOG_CONDITION_MEMBER, []), entry_gated)
        if entry_gated:
            refs.deferred.setdefault(where, set()).update(entry_gated)

        if not isinstance(key, str) or not RESEARCH_KEY.match(key):
            report.error(place, "research key %r is invalid" % key)
            continue
        refs.research_keys.add(key)
        refs.lang_keys.append(("tc.research_name." + serialized_research_key(key), place))
        refs.lang_keys.append(("tc.research_text." + serialized_research_key(key), place))

        category = entry.get("category")
        if category not in refs.categories:
            report.warn(place, "category %r is not declared in this file; TC4R throws"
                               " \"refers to missing category\" if no other pack declares it"
                        % category)
        for field in ("display_column", "display_row", "warp"):
            if field in entry and not isinstance(entry[field], int):
                report.error(place, "%s must be an integer, got %r" % (field, entry[field]))
        complexity = entry.get("complexity")
        if not isinstance(complexity, int):
            report.error(place, "complexity must be an integer, got %r" % complexity)
        elif not COMPLEXITY_RANGE[0] <= complexity <= COMPLEXITY_RANGE[1]:
            report.error(place, "complexity %d is outside %d..%d; ResearchEntryDefinition clamps"
                                " it silently, so the file would not say what the game does"
                         % (complexity, *COMPLEXITY_RANGE))
        for flag in entry.get("flags", []):
            if flag not in RESEARCH_FLAGS:
                report.error(place, "unknown flag %r (allowed: %s)"
                             % (flag, ", ".join(sorted(RESEARCH_FLAGS))))
        read_aspect_map(report, refs, place + " aspects", entry.get("aspects"))
        for field in ("parents", "hidden_parents", "siblings"):
            for parent in entry.get(field, []):
                if not isinstance(parent, str) or not RESEARCH_KEY.match(parent):
                    report.error(place, "%s entry %r is invalid" % (field, parent))
                else:
                    refs.research_parents.append((parent, "%s %s" % (place, field)))

        icon = entry.get("icon")
        if not isinstance(icon, dict):
            report.error(place, "icon is required and must be an object")
        else:
            icon_type = icon.get("type")
            if icon_type not in RESEARCH_ICON_FIELDS:
                report.error(place, "icon type %r must be one of %s"
                             % (icon_type, ", ".join(sorted(RESEARCH_ICON_FIELDS))))
            else:
                only(report, place + " icon", icon, RESEARCH_ICON_FIELDS[icon_type])
                if icon_type == "TEXTURE" and check_resource_location(
                        report, place + " icon", icon.get("texture"), "texture"):
                    refs.textures.append((icon["texture"], place + " icon"))
                if icon_type == "STACK":
                    read_result(report, refs, place + " icon stack", icon.get("stack"))

        pages = entry.get("pages")
        if not isinstance(pages, list) or not pages:
            report.error(place, "pages must be a non-empty array")
            continue
        for index, page in enumerate(pages):
            spot = "%s page %d" % (place, index + 1)
            if not isinstance(page, dict):
                report.error(spot, "page must be an object")
                continue
            only(report, spot, page, RESEARCH_PAGE_FIELDS)
            page_type = page.get("type")
            if page_type not in PAGE_TYPES:
                report.error(spot, "page type %r must be one of %s"
                             % (page_type, ", ".join(sorted(PAGE_TYPES))))
                continue
            if "aspects" not in page:
                report.error(spot, "\"aspects\" must be present even when empty:"
                                   " ResearchCatalog.aspectMap has no null guard and will NPE")
            else:
                read_aspect_map(report, refs, spot + " aspects", page["aspects"])
            policy = page.get("recipe_link_policy")
            if policy not in LINK_POLICIES:
                report.error(spot, "recipe_link_policy %r must be one of %s; it has no default"
                                   " (valueOf(\"\") throws)"
                             % (policy, ", ".join(sorted(LINK_POLICIES))))
            recipe_ids = page.get("recipe_ids", [])
            if page_type in RECIPE_PAGE_TYPES:
                if not recipe_ids:
                    report.error(spot, "a %s page must reference at least one recipe" % page_type)
                if policy == "COMPLETED_SINGLE" and len(recipe_ids) != 1:
                    report.error(spot, "COMPLETED_SINGLE requires exactly one recipe id, got %d"
                                 % len(recipe_ids))
                if policy == "COMPLETED_CRUCIBLE_VARIANTS" and page_type != "CRUCIBLE_CRAFTING":
                    report.error(spot, "only CRUCIBLE_CRAFTING pages may link recipe variants")
            else:
                if recipe_ids:
                    report.error(spot, "a %s page must not reference recipes" % page_type)
                if policy != "DISABLED":
                    report.error(spot, "a %s page must use recipe_link_policy DISABLED"
                                 % page_type)
                if page_type in ("TEXT", "TEXT_CONCEALED", "LORE"):
                    value = page.get("value")
                    if not isinstance(value, str) or not value:
                        report.error(spot, "a %s page needs a translation key in \"value\""
                                     % page_type)
                    else:
                        refs.lang_keys.append((value, spot))
            for recipe_id in recipe_ids:
                if check_resource_location(report, spot, recipe_id, "recipe id"):
                    refs.recipe_ids.append((recipe_id, spot + " (referenced)"))
            if (page_type == "COMPOUND_CRAFTING") != bool(page.get("compound_blueprint")):
                report.error(spot, "compound_blueprint is required by and exclusive to"
                                   " COMPOUND_CRAFTING pages")
            if (page_type == "EXTERNAL_LINK") != bool(page.get("external_link")):
                report.error(spot, "external_link is required by and exclusive to"
                                   " EXTERNAL_LINK pages")


def check_object_aspects(report: Report, refs: Refs, path: pathlib.Path, doc: object) -> None:
    where = rel(path)
    if not isinstance(doc, dict):
        report.error(where, "object_aspects root must be an object")
        return
    gated: "set[str]" = set()
    condition_items(doc.get(CATALOG_CONDITION_MEMBER, []), gated)
    if gated:
        refs.deferred.setdefault(where, set()).update(gated)
    for field in sorted(set(doc) & ASPECT_FORBIDDEN_ROOT):
        report.error(where, "%r is synchronized output, not game data; readFile rejects it"
                     % field)
    only(report, where, doc, ASPECT_ROOT_FIELDS)

    for item_id, aspects in (doc.get("direct") or {}).items():
        place = "%s direct[%s]" % (where, item_id)
        if check_resource_location(report, place, item_id, "item"):
            # An unknown id here is an IllegalStateException that drops the whole file.
            refs.items.append((item_id, place))
        read_aspect_map(report, refs, place, aspects)
    for index, entry in enumerate(doc.get("tags") or []):
        place = "%s tags[%d]" % (where, index)
        if not isinstance(entry, dict):
            report.error(place, "tag entry must be an object")
            continue
        only(report, place, entry, {"id", "rule_id", "aspects"}, {"id", "aspects"})
        if check_resource_location(report, place, entry.get("id"), "tag"):
            refs.tags.append((entry["id"], place))
        read_aspect_map(report, refs, place, entry.get("aspects"))


def load_lang() -> "dict[str, dict[str, str]]":
    loaded = {}
    for name in LANG_FILES:
        path = ASSETS / "lang" / name
        loaded[name] = json.load(io.open(path, encoding="utf-8")) if path.is_file() else {}
    return loaded


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--inventory", type=pathlib.Path,
                        default=ROOT / "run-gametest/technom-data-inventory.json",
                        help="registry dump written by the DataPackGameTests inventory test")
    parser.add_argument("--require-inventory", action="store_true",
                        help="fail instead of skipping when the inventory is absent")
    arguments = parser.parse_args()

    report = Report()
    refs = Refs()

    files = sorted(DATA.rglob("*.json"))
    if not files:
        print("no data files under %s" % rel(DATA))
        return 1
    parsed: "dict[pathlib.Path, object]" = {}
    for path in files:
        try:
            parsed[path] = json.load(io.open(path, encoding="utf-8"))
            report.ok()
        except ValueError as exception:
            report.error(rel(path), "invalid JSON: %s" % exception)

    recipe_files: "set[str]" = set()
    for path, doc in parsed.items():
        relative = path.relative_to(DATA)
        head = relative.parts[0]
        if head == "recipes":
            check_recipe(report, refs, path, doc)
            recipe_files.add("%s:%s" % (NAMESPACE,
                                        relative.relative_to("recipes").with_suffix("").as_posix()))
        elif relative.parts[:2] == ("thaumcraft", "research"):
            check_research(report, refs, path, doc)
        elif relative.parts[:2] == ("thaumcraft", "object_aspects"):
            check_object_aspects(report, refs, path, doc)
        elif head in ("loot_tables", "structures", "tags", "advancements"):
            continue
        else:
            report.warn(rel(path), "no schema known for this directory; only parsed")

    # --- cross references inside the repository -------------------------------------------
    for recipe_id, where in refs.recipe_ids:
        if recipe_id.startswith(NAMESPACE + ":") and recipe_id not in recipe_files:
            report.error(where, "recipe id %s has no file at"
                                " src/main/resources/data/%s/recipes/%s.json"
                         % (recipe_id, NAMESPACE, recipe_id.split(":", 1)[1]))
        else:
            report.ok()

    declared = {serialized_research_key(key): key for key in refs.research_keys}
    for parent, where in refs.research_parents:
        if parent.startswith(NAMESPACE + ":") and parent not in refs.research_keys:
            report.error(where, "research key %s is referenced but never declared" % parent)
        else:
            report.ok()

    lang = load_lang()
    for key, where in refs.lang_keys:
        for name, table in lang.items():
            if key not in table:
                report.error(where, "translation key %r is missing from assets/%s/lang/%s"
                             % (key, NAMESPACE, name))
            else:
                report.ok()
    if lang["en_us.json"].keys() != lang["zh_cn.json"].keys():
        difference = set(lang["en_us.json"]) ^ set(lang["zh_cn.json"])
        report.error("assets/%s/lang" % NAMESPACE,
                     "en_us and zh_cn key sets differ: %s" % sorted(difference))

    for texture, where in refs.textures:
        namespace, _, path = texture.partition(":")
        if namespace != NAMESPACE:
            continue
        target = ASSETS / path
        if not target.is_file():
            report.error(where, "texture %s has no file at %s" % (texture, rel(target)))
        else:
            report.ok()

    # --- registry resolution --------------------------------------------------------------
    gated_everywhere = {item for items in refs.deferred.values() for item in items}
    if arguments.inventory.is_file():
        inventory = json.load(io.open(arguments.inventory, encoding="utf-8"))
        known_items = set(inventory.get("items", []))
        known_tags = set(inventory.get("item_tags", []))
        known_aspects = set(inventory.get("aspects", []))
        known_serializers = set(inventory.get("recipe_serializers", []))
        print("inventory %s: %d items, %d item tags, %d aspects, %d recipes total"
              % (rel(arguments.inventory), len(known_items), len(known_tags),
                 len(known_aspects), inventory.get("recipe_total", -1)))

        for item_id, where in refs.items:
            if item_id in known_items:
                report.ok()
            elif item_id in gated_everywhere:
                report.skip("%s: %s is not registered yet but is gated by forge:item_exists"
                            % (where, item_id))
            else:
                report.error(where, "item %s is not in the item registry" % item_id)
        for tag_id, where in refs.tags:
            if tag_id in known_tags:
                report.ok()
            else:
                report.error(where, "item tag %s does not exist" % tag_id)
        for aspect, where in refs.aspects:
            if aspect in known_aspects or ("thaumcraft:" + aspect) in known_aspects:
                report.ok()
            else:
                report.error(where, "aspect %r does not exist" % aspect)
        for recipe_type in sorted({doc.get("type") for doc in parsed.values()
                                   if isinstance(doc, dict) and "type" in doc}):
            if recipe_type in RECIPE_SCHEMA and recipe_type not in known_serializers:
                report.error("recipe type", "%s has no registered serializer" % recipe_type)
        for ingredient_type, where in refs.ingredient_types:
            report.skip("%s: custom ingredient type %s is only checked by the GameTest"
                        % (where, ingredient_type))
        loaded_recipes = set(inventory.get("mod_recipes", {}))
        for recipe_id in sorted(recipe_files):
            gate = refs.deferred.get("src/main/resources/data/%s/recipes/%s.json"
                                     % (NAMESPACE, recipe_id.split(":", 1)[1]), set())
            if recipe_id in loaded_recipes:
                report.ok()
            elif gate and not gate <= known_items:
                report.skip("%s was not loaded; gated on %s" % (recipe_id, sorted(gate)))
            else:
                report.error(recipe_id, "authored but not present in the loaded recipe set")
        loaded_research = set(inventory.get("research_entries", {}))
        for key in sorted(refs.research_keys):
            if key in loaded_research:
                report.ok()
            else:
                report.skip("research %s was not loaded (expected when its content is gated off)"
                            % key)
    else:
        message = ("inventory %s is absent: item, tag, aspect and loaded-recipe checks were NOT"
                   " performed. Run `gradlew runGameTestServer` first."
                   % rel(arguments.inventory))
        if arguments.require_inventory:
            report.error("inventory", message)
        else:
            report.skip(message)

    # --- output ----------------------------------------------------------------------------
    print("validated %d files, %d checks" % (len(parsed), report.checks))
    for note in report.skipped:
        print("SKIP  %s" % note)
    for warning in report.warnings:
        print("WARN  %s" % warning)
    for error in report.errors:
        print("ERROR %s" % error)
    if report.errors:
        print("FAILED: %d error(s)" % len(report.errors))
        return 1
    print("OK: no errors (%d warning(s), %d skipped check group(s))"
          % (len(report.warnings), len(report.skipped)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
