#!/usr/bin/env python3
"""Start the dev client, let it bake its resources, and report asset failures.

This is the one check a dedicated server cannot make. Models, blockstates and
texture atlases are only ever loaded client-side, so a missing model, a
blockstate naming a property the block does not have, or a sprite that cannot be
stitched is invisible to `runServer`, `runGameTestServer` and the live probes -
all of which can pass while the blocks are untextured cubes in game.

The client is stopped as soon as its first resource reload has finished. That is
already past model baking and atlas stitching, so it is enough for this check,
and it needs no world and no player.

    python tools/client_check.py            # run and report
    python tools/client_check.py --keep     # leave the client running

Exit code is the number of asset problems attributable to this mod, so it can
gate a build. Problems from other mods are reported separately and do not fail
the run; we do not control their assets.

What this check does NOT cover: whether anything looks right. Geometry,
orientation, colour and z-order still need a human at a screen.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

import probe_harness

ROOT = Path(__file__).resolve().parents[1]
LOG = ROOT / "build" / "client-check.log"
MOD_ID = "technom"

# Resource loading is finished once the atlases exist; the sound engine and the
# narrator come up around the same point. Any one of these means baking is done.
DONE = re.compile(r"Created: \d+x\d+x\d+ minecraft:textures/atlas"
                  r"|Sound engine started"
                  r"|Narrator library for .* successfully loaded"
                  r"|Realms availability check")
FAILED = re.compile(r"BUILD FAILED|A problem occurred|Could not resolve|Exception in thread \"main\"")

# The ways 1.20.1 reports a broken asset. Each is a real failure, not a warning:
# the game substitutes a placeholder and carries on, which is exactly why these
# have to be read out of the log rather than waited for as a crash.
PROBLEMS = (
    re.compile(r"Unable to load model[: ]+(\S+)"),
    re.compile(r"Missing model[: ]+(\S+)"),
    re.compile(r"Exception loading blockstate definition[: ]+(\S+)"),
    re.compile(r"Unable to load variant[: ]+(\S+)"),
    re.compile(r"Using missing texture, unable to load (\S+)"),
    re.compile(r"Failed to load texture[: ]+(\S+)"),
    re.compile(r"Couldn't load client asset[: ]+(\S+)"),
    re.compile(r"Error loading blockstate[: ]+(\S+)"),
)


def wait_for_reload(client: subprocess.Popen, timeout: int) -> str:
    """Returns the marker line that proved resource loading finished."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if client.poll() is not None:
            raise SystemExit(f"client exited with {client.returncode} before loading resources; see {LOG}")
        text = LOG.read_text(encoding="utf-8", errors="replace") if LOG.is_file() else ""
        found = DONE.search(text)
        if found:
            return found.group(0)
        if FAILED.search(text):
            raise SystemExit(f"client failed to start; see {LOG}")
        time.sleep(3)
    raise SystemExit(f"client did not finish a resource reload within {timeout}s; see {LOG}")


def collect(text: str) -> tuple[list[str], list[str]]:
    """Asset problems, split into ours and everyone else's."""
    ours: list[str] = []
    theirs: list[str] = []
    for line in text.splitlines():
        for pattern in PROBLEMS:
            hit = pattern.search(line)
            if not hit:
                continue
            entry = line.strip()
            (ours if MOD_ID in hit.group(0) else theirs).append(entry)
            break
    return ours, theirs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--timeout", type=int, default=600, help="seconds to wait for the resource reload")
    parser.add_argument("--keep", action="store_true", help="leave the client running afterwards")
    args = parser.parse_args()

    LOG.parent.mkdir(parents=True, exist_ok=True)
    LOG.write_bytes(b"")
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    with LOG.open("wb") as sink:
        client = subprocess.Popen([str(gradlew), "runClient", "--console=plain", "--no-daemon"],
                                  cwd=ROOT, stdout=sink, stderr=subprocess.STDOUT)
        try:
            print(f"starting the client, waiting for its resource reload (log: {LOG})")
            marker = wait_for_reload(client, args.timeout)
            print(f"resources loaded: {marker!r}\n")
            # The reload logs asynchronously around that marker; give it a moment
            # to finish writing before reading the log back.
            time.sleep(10)
            ours, theirs = collect(LOG.read_text(encoding="utf-8", errors="replace"))

            print(f"{MOD_ID} asset problems: {len(ours)}")
            for line in ours:
                print(f"  {line}")
            if theirs:
                print(f"\nother mods' asset problems (not ours to fix): {len(theirs)}")
                for line in theirs[:10]:
                    print(f"  {line}")
                if len(theirs) > 10:
                    print(f"  ... and {len(theirs) - 10} more")
            if not ours:
                print(f"\nno {MOD_ID} model, blockstate or texture failed to load")
            return len(ours)
        finally:
            if args.keep:
                print("\n--keep: the client is still running; close it yourself")
            else:
                # A client at the title screen holds no world state, so there is nothing to
                # flush and no graceful-stop path to honour. It does need a TREE kill: this
                # never joins a world, so the debug bridge is not listening and cannot be
                # asked to close the game, and killing only the gradle wrapper we hold would
                # leave the forked client JVM running - which is exactly what happened here,
                # twice, before this was noticed.
                probe_harness.kill_tree(client)


if __name__ == "__main__":
    sys.exit(main())
