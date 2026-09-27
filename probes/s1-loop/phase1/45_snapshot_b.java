//@wait 3
// Second reading three seconds later. It must equal the first, or the restart comparison in
// phase 2 would be measuring motion rather than persistence. This one is what phase 2 checks.
//@include _snapshot.frag
String a = java.nio.file.Files.readString(java.nio.file.Path.of("technom-probe-snapshot-a.txt"));
java.nio.file.Files.writeString(java.nio.file.Path.of("technom-probe-snapshot.txt"), snap.toString());
return "snapshot B: " + snap + "\n"
        + (a.equals(snap.toString()) ? "PASS: the halted state is static" : "FAIL: still changing\n  A=" + a) + "\n";
