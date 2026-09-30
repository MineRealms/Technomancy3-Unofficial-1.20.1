#!/usr/bin/env python3
"""Builds a JEI API compatibility matrix for the 1.20.1-Forge line.

For every requested JEI version this reports whether the exact symbols the three
consumers in this pack need are present:

  * Technomancy  -- the Technomancy plugin's own category (drawn by hand, no
                    version-sensitive API beyond `IRecipeCategory`/`IRecipeLayoutBuilder`)
  * TC4R 0.1.0-20721 -- `mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter`
                        and `ITextWidget.setPosition(int, int)` returning `ITextWidget`
  * GTCEu 7.5.3 -- the `jei.FluidHelperMixin` injection target
                   `mezz.jei.forge.platform.FluidHelper#getTooltip(ITooltipBuilder, FluidStack, TooltipFlag)`
                   plus the `ITooltipBuilder.add(net.minecraft.network.chat.FormattedText)`
                   descriptor its method reference binds to.

Usage:  python tools/jei_api_matrix.py 15.20.0.115 15.62.0.216 ...
"""
import os
import re
import subprocess
import sys
import zipfile
import urllib.request

BASE = "https://maven.blamejared.com/mezz/jei"
ROOT = os.path.join(os.environ.get("TEMP", "/tmp"), "jei-matrix")
CACHE = os.path.join(ROOT, "cache")
WORK = os.path.join(ROOT, "work")


def fetch(artifact: str, version: str) -> str | None:
    """Download (once) and return the path of an artifact jar, or None."""
    os.makedirs(CACHE, exist_ok=True)
    jar = os.path.join(CACHE, f"{artifact}-{version}.jar")
    if os.path.exists(jar) and zipfile.is_zipfile(jar):
        return jar
    url = f"{BASE}/{artifact}/{version}/{artifact}-{version}.jar"
    try:
        with urllib.request.urlopen(url, timeout=180) as r:
            data = r.read()
    except Exception:
        return None
    tmp = jar + ".part"
    with open(tmp, "wb") as f:
        f.write(data)
    if not zipfile.is_zipfile(tmp):
        os.remove(tmp)
        return None
    os.replace(tmp, jar)
    return jar


def unpack(version: str) -> str | None:
    """Extract the mod jar (and its api jar) into a per-version directory."""
    d = os.path.join(WORK, version)
    if os.path.isdir(d):
        return d
    jars = [j for j in (fetch("jei-1.20.1-forge", version),
                        fetch("jei-1.20.1-forge-api", version)) if j]
    if not jars:
        return None
    os.makedirs(d, exist_ok=True)
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            z.extractall(d)
    return d


def javap(d: str, fqcn: str) -> str:
    """Disassemble a class from the extracted tree. Empty string if absent."""
    path = os.path.join(d, fqcn.replace(".", os.sep) + ".class")
    if not os.path.exists(path):
        return ""
    out = subprocess.run(["javap", "-p", "-classpath", d, fqcn],
                         capture_output=True, text=True,
                         env={**os.environ, "LANG": "C", "LC_ALL": "C"})
    return out.stdout


def report(version: str) -> dict:
    d = unpack(version)
    row = {"version": version}
    if d is None:
        row["error"] = "jar unavailable"
        return row

    # --- TC4R -------------------------------------------------------------
    row["isubtype_interpreter"] = bool(
        javap(d, "mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter"))
    tw = javap(d, "mezz.jei.api.gui.widgets.ITextWidget")
    row["textwidget_setposition"] = len(
        re.findall(r"ITextWidget setPosition\(int, int\)", tw))
    row["textwidget_super"] = next(
        (l.strip() for l in tw.splitlines() if "interface" in l), "")

    # --- GTCEu ------------------------------------------------------------
    fh = javap(d, "mezz.jei.forge.platform.FluidHelper")
    row["fluidhelper_gettooltip"] = len(re.findall(
        r"getTooltip\(mezz\.jei\.api\.gui\.builder\.ITooltipBuilder, "
        r"net\.minecraftforge\.fluids\.FluidStack, "
        r"net\.minecraft\.world\.item\.TooltipFlag\)", fh))
    itb = javap(d, "mezz.jei.api.gui.builder.ITooltipBuilder")
    row["itooltipbuilder_add_formattedtext"] = len(re.findall(
        r"add\(net\.minecraft\.network\.chat\.FormattedText\)", itb))
    row["itooltipbuilder_add_component"] = len(re.findall(
        r"add\(net\.minecraft\.network\.chat\.Component\)", itb))
    return row


def verdict(row: dict) -> str:
    if row.get("error"):
        return "UNAVAILABLE"
    tc4r = row["isubtype_interpreter"] and row["textwidget_setposition"] > 0
    gtceu = row["fluidhelper_gettooltip"] > 0 and row["itooltipbuilder_add_formattedtext"] > 0
    if tc4r and gtceu:
        return "BOTH OK"
    if gtceu:
        return "GTCEu only"
    if tc4r:
        return "TC4R only"
    return "NEITHER"


def main(argv: list[str]) -> int:
    print(f"{'version':<16} {'ISubtype':<9} {'ITW.setPos':<11} "
          f"{'FH.getTooltip':<14} {'ITB.add(FT)':<12} {'verdict'}")
    print("-" * 82)
    for version in argv:
        row = report(version)
        if row.get("error"):
            print(f"{version:<16} {row['error']}")
            continue
        print(f"{version:<16} {str(row['isubtype_interpreter']):<9} "
              f"{row['textwidget_setposition']:<11} "
              f"{row['fluidhelper_gettooltip']:<14} "
              f"{row['itooltipbuilder_add_formattedtext']:<12} {verdict(row)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
