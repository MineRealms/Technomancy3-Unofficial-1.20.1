//@wait 20
// The loop must be running again after the restart: the jar keeps draining into the
// dynamo and energy keeps reaching the condenser.
//@include _snapshot.frag
java.util.Map<String, String> before = new java.util.LinkedHashMap<String, String>();
for (String part : java.nio.file.Files.readString(java.nio.file.Path.of("technom-probe-resume.txt"))
        .replaceAll("^\\{|\\}$", "").split(", (?=[a-z0-9.]+=)")) {
    int eq = part.indexOf('=');
    before.put(part.substring(0, eq), part.substring(eq + 1));
}
java.util.List<String> bad = new java.util.ArrayList<String>();
int jarBefore = Integer.parseInt(before.get("jar.amount"));
int jarAfter = Integer.parseInt(snap.get("jar.amount"));
long condenserBefore = Long.parseLong(before.get("condenser.unfinishedQ")) + Long.parseLong(before.get("condenser.storedQ"));
long condenserAfter = Long.parseLong(snap.get("condenser.unfinishedQ")) + Long.parseLong(snap.get("condenser.storedQ"));
if (jarAfter >= jarBefore) bad.add("the jar did not drain after resuming: " + jarBefore + " -> " + jarAfter);
if (condenserAfter == condenserBefore && snap.get("sink").equals(before.get("sink"))) {
    bad.add("no energy reached the condenser after resuming");
}
return "jar " + jarBefore + " -> " + jarAfter + ", condenser energy " + condenserBefore + " -> " + condenserAfter
        + ", sink " + before.get("sink") + " -> " + snap.get("sink") + "\n"
        + (bad.isEmpty() ? "PASS" : "FAIL: " + bad) + "\n";
