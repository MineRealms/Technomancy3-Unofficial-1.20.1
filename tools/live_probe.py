#!/usr/bin/env python3
"""Run a batch of probes against one dedicated-server session.

Starting a server costs roughly a minute, so verification is batched: this
starts `runServer` once, waits for the RosettaRemoteDebugBridge to listen, runs
every probe in order, then stops the server gracefully so shutdown-time
persistence actually runs.

    python tools/live_probe.py probes/essentia                # a directory
    python tools/live_probe.py probes/essentia/10_jar.java     # or one file
    python tools/live_probe.py probes/a probes/b               # several, one session
    python tools/live_probe.py probes/essentia --keep-alive    # leave it running

The probe format, the //@include and //@wait directives and the bridge wire
protocol are documented in probe_harness.py, which client_probe.py shares.

One trap specific to a dedicated server with nobody on it: with no player
online NO chunk ticks, so block entities never run. Call
`level.setChunkForced(x, z, true)` before expecting anything to tick, or a dead
tick counter reads as broken logic.

Exit code is the number of failed probes, so a build can gate on it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import probe_harness as harness

RUN_DIR = harness.ROOT / "run-server"
TOKEN_FILE = RUN_DIR / "RosettaRemoteDebugBridge" / "remote-token.txt"
LOG = harness.ROOT / "build" / "live-probe-server.log"
PORT = 48790


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("probes", type=Path, nargs="+",
                        help="probe files or directories, run in the order given")
    parser.add_argument("--timeout", type=int, default=300, help="seconds to wait for the bridge")
    parser.add_argument("--keep-alive", action="store_true", help="leave the server running afterwards")
    args = parser.parse_args()

    files = harness.probe_files(args.probes)
    if not files:
        raise SystemExit(f"no probes found in {args.probes}")
    harness.require_bridge(RUN_DIR)

    server = harness.launch(["runServer"], LOG)
    try:
        print(f"starting the server, waiting for the bridge (log: {LOG})")
        harness.wait_for_bridge(server, LOG, args.timeout, "the server")
        print(f"bridge up on {PORT}; running {len(files)} probe(s)\n")
        return harness.run_probes(files, PORT, TOKEN_FILE)
    finally:
        if args.keep_alive:
            print("\n--keep-alive: the server is still running; stop it with the bridge `console stop`")
        else:
            try:
                harness.call(PORT, TOKEN_FILE, "console", {"command": "stop"}, timeout=30)
            except OSError as unreachable:
                print(f"could not reach the bridge to stop the server: {unreachable}", file=sys.stderr)
            try:
                server.wait(timeout=180)
            except Exception:
                print("server did not stop within 180s; killing its process tree", file=sys.stderr)
                harness.kill_tree(server)


if __name__ == "__main__":
    sys.exit(main())
