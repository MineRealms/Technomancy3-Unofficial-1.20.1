#!/usr/bin/env python3
"""Shared machinery for driving probes through the RosettaRemoteDebugBridge.

Both entry points build on this: `live_probe.py` drives a dedicated server,
`client_probe.py` drives a client that has quick-joined a world. They differ
only in what they launch, which port the bridge listens on and how they shut
it down; everything else - the wire protocol, the //@include and //@wait
directives, the pass/fail accounting - lives here so the two cannot drift
apart.

A probe is a Java *method body* executed on the game thread, which must
`return` a value. Imports are not available, so use fully qualified names;
inner-class return types sometimes fail to resolve, so call those reflectively.
A probe reports its own verdict by printing PASS or FAIL in its result.
"""

from __future__ import annotations

import json
import re
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE_PROJECT = Path("H:/MinecraftMods/RainJava-work/RosettaRemoteDebugBridge")
DEV_JAR = "rosetta_bridge_dev.jar"

WAIT_DIRECTIVE = re.compile(r"\s*//@wait\s+([0-9]+(?:\.[0-9]+)?)")
INCLUDE_DIRECTIVE = re.compile(r"^[ \t]*//@include[ \t]+(\S+)[ \t]*$", re.M)
BRIDGE_READY = re.compile(r"Remote bridge listening on")
LAUNCH_FAILED = re.compile(r"BUILD FAILED|A problem occurred|Could not resolve"
                           r"|Exception in thread \"main\"")

BRIDGE_HELP = (
    f"Deploy a dev-mapped bridge jar as <run dir>/mods/{DEV_JAR} first.\n"
    f"  The prebuilt jar in {BRIDGE_PROJECT}\\build\\libs is SRG-reobfuscated and will not\n"
    "  load in a ForgeGradle dev environment, so build a dev-mapped one and put the\n"
    "  original artifact back afterwards (verify with cmp):\n"
    "    cp build/libs/rosetta_remote_debug_bridge-1.0.0.jar /tmp/backup.jar\n"
    "    ./gradlew.bat jar -x reobfJar --no-daemon\n"
    "    cp build/libs/rosetta_remote_debug_bridge-1.0.0.jar <target>/mods/" + DEV_JAR + "\n"
    "    cp /tmp/backup.jar build/libs/rosetta_remote_debug_bridge-1.0.0.jar"
)


def require_bridge(run_dir: Path) -> None:
    if not (run_dir / "mods" / DEV_JAR).is_file():
        raise SystemExit(f"{run_dir / 'mods' / DEV_JAR} is missing.\n{BRIDGE_HELP}")


def call(port: int, token_file: Path, cmd: str, args: dict, timeout: float = 180) -> dict:
    """One request per connection, one JSON object per line."""
    token = token_file.read_text(encoding="utf-8").strip()
    payload = json.dumps({"token": token, "cmd": cmd, "args": args}) + "\n"
    with socket.create_connection(("127.0.0.1", port), timeout=timeout) as sock:
        sock.sendall(payload.encode("utf-8"))
        chunks: list[bytes] = []
        while not chunks or not chunks[-1].endswith(b"\n"):
            chunk = sock.recv(1 << 20)
            if not chunk:
                break
            chunks.append(chunk)
    return json.loads(b"".join(chunks).decode("utf-8"))


def wait_for_bridge(process: subprocess.Popen, log: Path, timeout: int, what: str) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise SystemExit(f"{what} exited with {process.returncode} before the bridge came up; see {log}")
        text = log.read_text(encoding="utf-8", errors="replace") if log.is_file() else ""
        if BRIDGE_READY.search(text):
            return
        if LAUNCH_FAILED.search(text):
            raise SystemExit(f"{what} failed to start; see {log}")
        time.sleep(3)
    raise SystemExit(f"the bridge did not come up within {timeout}s; see {log}")


def probe_files(targets: list[Path]) -> list[Path]:
    """Probes in the order given; a directory contributes its *.java files in name order."""
    files: list[Path] = []
    for target in targets:
        if target.is_file():
            files.append(target)
        elif target.is_dir():
            files.extend(sorted(target.glob("*.java")))
        else:
            raise SystemExit(f"no such probe path: {target}")
    return files


def expand_includes(path: Path) -> str:
    """Inline every //@include, searching the probe's directory and then its parents."""
    def resolve(match: re.Match[str]) -> str:
        name = match.group(1)
        for folder in [path.parent, *path.parent.parents]:
            candidate = folder / name
            if candidate.is_file():
                return candidate.read_text(encoding="utf-8")
            if folder == ROOT:
                break
        raise SystemExit(f"{path}: cannot find included {name}")
    return INCLUDE_DIRECTIVE.sub(resolve, path.read_text(encoding="utf-8"))


def run_probes(files: list[Path], port: int, token_file: Path) -> int:
    """Runs each probe in order and returns how many failed."""
    failures = 0
    for path in files:
        source = expand_includes(path)
        # Probes run back to back, but tick-driven behaviour needs real time to happen,
        # so a probe may ask to be delayed with a leading `//@wait <seconds>`.
        delay = WAIT_DIRECTIVE.match(source)
        if delay:
            seconds = float(delay.group(1))
            print(f"waiting {seconds:g}s before {path.name} (probe asked for it)")
            time.sleep(seconds)
        print(f"{'=' * 12} {path.name} {'=' * 12}")
        try:
            response = call(port, token_file, "exec", {"code": source})
        except OSError as unreachable:
            print(f"  TRANSPORT ERROR: {unreachable}")
            failures += 1
            continue
        if not response.get("ok", True):
            print(f"  BRIDGE ERROR: {json.dumps(response, ensure_ascii=False)}")
            failures += 1
            continue
        # Success shape is {"ok":true,"result":{"result":"<value>","class":"..."}}.
        result = response.get("result")
        if isinstance(result, dict):
            result = result.get("result")
        if result is None:
            print(f"  NO RESULT: {json.dumps(response, ensure_ascii=False)}")
            failures += 1
            continue
        print(result)
        if "FAIL" in str(result):
            failures += 1
        print()
    print(f"{len(files) - failures}/{len(files)} probes passed")
    return failures


def kill_tree(process: subprocess.Popen) -> None:
    """Last resort: kill the launched process AND its descendants.

    Popen starts gradlew.bat, which starts a java wrapper, which forks the game's own
    JVM. Killing only the object we hold leaves the whole java tree running - two
    orphaned Minecraft clients were found that way, hours after their runs "finished".
    Only ever call this after a graceful stop has been tried and has not worked.
    """
    if process.poll() is not None:
        return
    if sys.platform == "win32":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                       capture_output=True, check=False)
    else:
        process.terminate()
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        process.kill()


def launch(gradle_args: list[str], log: Path) -> subprocess.Popen:
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_bytes(b"")
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    with log.open("wb") as sink:
        return subprocess.Popen([str(gradlew), *gradle_args, "--console=plain", "--no-daemon"],
                                cwd=ROOT, stdout=sink, stderr=subprocess.STDOUT)
