//@wait 5
// First reading after the machines have been halted for five seconds.
//@include _snapshot.frag
java.nio.file.Files.writeString(java.nio.file.Path.of("technom-probe-snapshot-a.txt"), snap.toString());
return "snapshot A: " + snap + "\nPASS\n";
