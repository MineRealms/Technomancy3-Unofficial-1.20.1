#!/usr/bin/env python3
"""Resolve merge conflicts where both sides appended their own block.

Every S2 group appends a clearly delimited registration block to the shared
registry and lang files, so the correct resolution is always "keep both", in
order. A regex over the whole file gets this wrong the moment a file has more
than one conflict; this is a line-based state machine, which does not.
"""
import sys
from pathlib import Path

def resolve(path: Path) -> int:
    out, ours, theirs, state, n = [], [], [], "plain", 0
    for line in path.read_text(encoding="utf-8").split("\n"):
        if line.startswith("<<<<<<< "):
            state, ours, theirs = "ours", [], []
        elif line.startswith("=======") and state == "ours":
            state = "theirs"
        elif line.startswith(">>>>>>> ") and state == "theirs":
            out.extend(ours)
            if ours and ours[-1].strip():
                out.append("")
            out.extend(theirs)
            state, n = "plain", n + 1
        elif state == "ours":
            ours.append(line)
        elif state == "theirs":
            theirs.append(line)
        else:
            out.append(line)
    assert state == "plain", f"{path}: unterminated conflict"
    path.write_text("\n".join(out), encoding="utf-8")
    return n

total = 0
for arg in sys.argv[1:]:
    p = Path(arg)
    c = resolve(p)
    total += c
    print(f"{p.name}: resolved {c} conflict(s)")
print(f"{total} total")
