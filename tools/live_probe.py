#!/usr/bin/env python3
"""Run a batch of live probes against one dev-server session.

Starting a server costs roughly a minute, so verification is batched: this
starts `runServer` once, waits for the RosettaRemoteDebugBridge to listen,
executes every probe in a directory in filename order, then stops the server
gracefully so shutdown-time persistence actually runs.

    python tools/live_probe.py probes/essentia          # run a directory
    python tools/live_probe.py probes/essentia/10_jar.java   # or one file
    python tools/live_probe.py probes/a probes/b        # several, in one server session
    python tools/live_probe.py probes/essentia --keep-alive  # leave it running

Each probe is a Java *method body* that must `return` a value; it is compiled
and executed on the server thread. A line `//@include name.frag` is replaced by
that file, looked up in the probe's directory and then its parents, so several
probes can share one piece of reading code instead of drifting copies.
Use fully qualified names - imports are not available - and note that
inner-class return types sometimes fail to resolve, so call those reflectively.

Two things every probe must account for, both learned the hard way:
  * With no player online NO chunk ticks, so block entities never run. Call
    `level.setChunkForced(x, z, true)` before expecting anything to tick, or a
    dead tick counter will read as broken logic.
  * The bridge's prebuilt jar is SRG-reobfuscated and will not load in a
    ForgeGradle dev environment. Build a dev-mapped one with
    `gradlew jar -x reobfJar` and restore the original artifact afterwards.

Exit code is the number of probes that failed, so CI can gate on it.
"""

from __future__ import annotations

import argparse
import json
import re
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = Path("H:/MinecraftMods/RainJava-work/RosettaRemoteDebugBridge")
RUN_DIR = ROOT / "run-server"
TOKEN_FILE = RUN_DIR / "RosettaRemoteDebugBridge" / "remote-token.txt"
LOG = ROOT / "build" / "live-probe-server.log"
BRIDGE_PORT = 48790
READY = re.compile(r"Remote bridge listening on")
FAILED = re.compile(r"BUILD FAILED|Exception in thread \"main\"|A problem occurred")
WAIT_DIRECTIVE = re.compile(r"\s*//@wait\s+([0-9]+(?:\.[0-9]+)?)")
INCLUDE_DIRECTIVE = re.compile(r"^[ \t]*//@include[ \t]+(\S+)[ \t]*$", re.M)


def call(cmd: str, args: dict) -> dict:
    """One request per connection, one JSON object per line."""
    token = TOKEN_FILE.read_text(encoding="utf-8").strip()
    payload = json.dumps({"token": token, "cmd": cmd, "args": args}) + "\n"
    with socket.create_connection(("127.0.0.1", BRIDGE_PORT), timeout=180) as sock:
        sock.sendall(payload.encode("utf-8"))
        chunks = []
        while not chunks or not chunks[-1].endswith(b"\n"):
            chunk = sock.recv(1 << 20)
            if not chunk:
                break
            chunks.append(chunk)
    return json.loads(b"".join(chunks).decode("utf-8"))


def wait_for_bridge(server: subprocess.Popen, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if server.poll() is not None:
            raise SystemExit(f"server exited early with {server.returncode}; see {LOG}")
        text = LOG.read_text(encoding="utf-8", errors="replace") if LOG.is_file() else ""
        if READY.search(text):
            return
        if FAILED.search(text):
            raise SystemExit(f"server failed to start; see {LOG}")
        time.sleep(3)
    raise SystemExit(f"bridge did not come up within {timeout}s; see {LOG}")


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


def stop_server(server: subprocess.Popen) -> None:
    try:
        call("console", {"command": "stop"})
    except OSError as unreachable:
        print(f"could not reach the bridge to stop the server: {unreachable}", file=sys.stderr)
    try:
        server.wait(timeout=180)
    except subprocess.TimeoutExpired:
        print("server did not stop within 180s; terminating", file=sys.stderr)
        server.terminate()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("probes", type=Path, nargs="+",
                        help="probe files or directories of *.java probes, run in the order given")
    parser.add_argument("--timeout", type=int, default=300, help="seconds to wait for the bridge")
    parser.add_argument("--keep-alive", action="store_true", help="leave the server running afterwards")
    args = parser.parse_args()

    files = probe_files(args.probes)
    if not files:
        raise SystemExit(f"no probes found in {args.probes}")
    if not (RUN_DIR / "mods").is_dir():
        raise SystemExit(f"{RUN_DIR / 'mods'} is missing: deploy a dev-mapped bridge jar first (see the module docstring)")

    LOG.parent.mkdir(parents=True, exist_ok=True)
    LOG.write_bytes(b"")
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    with LOG.open("wb") as sink:
        server = subprocess.Popen([str(gradlew), "runServer", "--console=plain", "--no-daemon"],
                                  cwd=ROOT, stdout=sink, stderr=subprocess.STDOUT)
        try:
            print(f"starting server, waiting for the bridge (log: {LOG})")
            wait_for_bridge(server, args.timeout)
            print(f"bridge up; running {len(files)} probe(s)\n")

            failures = 0
            for path in files:
                source = expand_includes(path)
                # Probes run back to back, but anything tick-driven needs real time to
                # happen, so a probe may ask to be delayed with a leading `//@wait <seconds>`.
                delay = WAIT_DIRECTIVE.match(source)
                if delay:
                    seconds = float(delay.group(1))
                    print(f"waiting {seconds:g}s before {path.name} (probe asked for it)")
                    time.sleep(seconds)
                print(f"{'=' * 12} {path.name} {'=' * 12}")
                try:
                    response = call("exec", {"code": source})
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
                # A probe signals its own verdict; anything containing FAIL is a failure.
                if "FAIL" in str(result):
                    failures += 1
                print()

            print(f"{len(files) - failures}/{len(files)} probes passed")
            return failures
        finally:
            if args.keep_alive:
                print("--keep-alive: leaving the server running; stop it with the bridge `console stop`")
            else:
                stop_server(server)


if __name__ == "__main__":
    sys.exit(main())
