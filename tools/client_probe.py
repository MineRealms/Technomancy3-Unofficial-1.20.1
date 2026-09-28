#!/usr/bin/env python3
"""Drive the dev client: quick-join a world, run probes, close it.

This covers what no server-side test can. Models, blockstates and texture
atlases exist only on a client, so a missing model, a blockstate naming a
property the block does not have, or an unregistered BlockEntityRenderer is
invisible to `runServer`, `runGameTestServer` and the server-side probes - all
of which pass happily while the blocks are untextured cubes in game.

The client quick-joins a world because the RosettaRemoteDebugBridge only starts
listening on ServerStartedEvent, which on a client means "a world was joined",
not "the title screen appeared". A run therefore needs a save to join; one is
created on first use by copying a template world, so nothing a human made is
ever touched.

    python tools/client_probe.py probes/client          # run and close
    python tools/client_probe.py probes/client --keep    # leave it running

Exit code is the number of failed probes, so a build can gate on it.

What this still does NOT cover: whether anything *looks* right. Geometry,
orientation, colour and z-order need a human at a screen.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import probe_harness as harness

RUN_DIR = harness.ROOT / "run-client"
SAVES = RUN_DIR / "saves"
TOKEN_FILE = RUN_DIR / "RosettaRemoteDebugBridge" / "remote-token.txt"
LOG = harness.ROOT / "build" / "client-probe.log"
PORT = 48791
DEFAULT_WORLD = "technom-autotest"
# A flat world the dedicated-server probes already generated. Copying it keeps the
# automated client run off any world a human created, and a flat world makes anything
# placed for a visual check easy to find.
TEMPLATE = harness.ROOT / "run-server" / "world-probe"

# Closing the client has to happen on its own thread, and Minecraft.stop() from a
# probe thread would race the render loop.
SHUTDOWN = """
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
mc.execute(() -> { try { mc.stop(); } catch (Throwable ignored) { } });
return "shutdown scheduled";
"""


def ensure_world(name: str) -> None:
    save = SAVES / name
    if save.is_dir():
        print(f"joining existing save {save}")
        return
    if not TEMPLATE.is_dir():
        raise SystemExit(
            f"no save {save} and no template at {TEMPLATE}.\n"
            "  Run the server probes once to generate one (python tools/live_probe.py probes/essentia),\n"
            f"  or copy any world into {SAVES} and pass its folder name with --world.")
    SAVES.mkdir(parents=True, exist_ok=True)
    # A dedicated-server world folder has the same layout as a client save, so a plain
    # copy is enough; session.lock is recreated by whoever opens it.
    shutil.copytree(TEMPLATE, save, ignore=shutil.ignore_patterns("session.lock"))
    print(f"created save {save} from {TEMPLATE}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("probes", type=Path, nargs="+",
                        help="probe files or directories, run in the order given")
    parser.add_argument("--world", default=DEFAULT_WORLD, help="save folder to quick-join")
    parser.add_argument("--timeout", type=int, default=900,
                        help="seconds to wait for the client to load a world and its bridge")
    parser.add_argument("--attach", action="store_true",
                        help="run against an already-running client's bridge (started detached) "
                             "instead of launching one and closing it")
    parser.add_argument("--keep", action="store_true", help="leave the client running afterwards")
    args = parser.parse_args()

    files = harness.probe_files(args.probes)
    if not files:
        raise SystemExit(f"no probes found in {args.probes}")
    if args.attach:
        if not TOKEN_FILE.is_file():
            raise SystemExit(f"no bridge token at {TOKEN_FILE}; has the client started?")
        print(f"attaching to the client bridge on {PORT}; running {len(files)} probe(s)\n")
        return harness.run_probes(files, PORT, TOKEN_FILE)
    harness.require_bridge(RUN_DIR)
    ensure_world(args.world)

    client = harness.launch(["runClient", f"-PquickPlay={args.world}"], LOG)
    try:
        print(f"starting the client, quick-joining {args.world!r}, waiting for its bridge (log: {LOG})")
        harness.wait_for_bridge(client, LOG, args.timeout, "the client")
        print(f"bridge up on {PORT}; running {len(files)} probe(s)\n")
        return harness.run_probes(files, PORT, TOKEN_FILE)
    finally:
        if args.keep:
            print("\n--keep: the client is still running; close it yourself")
        else:
            try:
                harness.call(PORT, TOKEN_FILE, "exec", {"code": SHUTDOWN}, timeout=30)
                print("\nasked the client to close itself")
            except OSError as unreachable:
                print(f"\ncould not reach the bridge to close the client: {unreachable}", file=sys.stderr)
            try:
                client.wait(timeout=120)
                print("client closed")
            except Exception:
                print("client did not close in time; killing its process tree", file=sys.stderr)
                harness.kill_tree(client)


if __name__ == "__main__":
    sys.exit(main())
