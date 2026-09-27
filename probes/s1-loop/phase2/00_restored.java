// Runs in a NEW server session over the same world: every piece of state recorded just
// before the graceful stop must come back exactly - essentia in the jar, tubes and machines,
// banked and stored energy, the condenser's half-made unit, the potency gem, facings,
// redstone modes (and whether the player changed them, which decides item refunds), and the
// condenser's per-face output configuration held in its block state.
//@include _snapshot.frag
java.nio.file.Path saved = java.nio.file.Path.of("technom-probe-snapshot.txt");
if (!java.nio.file.Files.exists(saved)) {
    return "FAIL: no snapshot from phase 1 at " + saved.toAbsolutePath() + "\n";
}
String before = java.nio.file.Files.readString(saved);
String after = snap.toString();
StringBuilder out = new StringBuilder("before: ").append(before).append("\nafter:  ").append(after).append("\n");
out.append(before.equals(after) ? "PASS: everything survived the restart" : "FAIL: state changed across the restart").append("\n");
return out.toString();
