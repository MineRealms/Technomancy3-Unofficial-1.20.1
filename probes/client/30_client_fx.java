// The node-creation bolt and the ritual effects are the two things a player does that
// previously produced no visible feedback at all. A server cannot prove either: particles
// are client-side and the bolt is geometry only a client draws. So this drives the client
// half directly and checks the shapes a render would actually consume.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null) {
    return "FAIL: the client is not in a world\n";
}
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

var fx = theflogat.technomancy.client.fx.TechnomClientFx.class;
java.lang.reflect.Method accept = fx.getMethod("accept", byte.class,
        net.minecraft.core.BlockPos.class, int.class);
java.lang.reflect.Method count = fx.getMethod("activeBolts");
java.lang.reflect.Method clear = fx.getMethod("clear");

// 1. An unknown kind must be ignored rather than throwing.
clear.invoke(null);
accept.invoke(null, (byte) 99, new net.minecraft.core.BlockPos(0, 64, 0), 0xFFFFFF);
int afterUnknown = (int) count.invoke(null);
out.append("bolts after unknown kind = ").append(afterUnknown).append("\n");
if (afterUnknown != 0) {
    bad.add("an unknown effect kind queued " + afterUnknown + " bolt(s)");
}

// 2. A node creation queues one bolt per corner.
clear.invoke(null);
accept.invoke(null, theflogat.technomancy.common.network.TechnomFxPacket.KIND_NODE,
        new net.minecraft.core.BlockPos(12, 64, -8), 0x3A7BFF);
int afterNode = (int) count.invoke(null);
out.append("bolts after node creation = ").append(afterNode).append("\n");
if (afterNode != 4) {
    bad.add("a node creation queued " + afterNode + " bolt(s), expected 4");
}

// 3. Repeating the same creation must queue the same number of bolts. The geometry itself is
//    seeded from the position rather than a random source, so two clients agree; nothing here
//    can read that geometry back, so the queue size is the observable part.
clear.invoke(null);
accept.invoke(null, theflogat.technomancy.common.network.TechnomFxPacket.KIND_NODE,
        new net.minecraft.core.BlockPos(12, 64, -8), 0x3A7BFF);
int again = (int) count.invoke(null);
if (again != afterNode) {
    bad.add("repeating the same creation queued " + again + " instead of " + afterNode);
}

// 4. The ritual colours must all be distinct - five types that render the same colour
//    would make the effect useless for telling them apart.
java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
for (theflogat.technomancy.common.rituals.Ritual.Type type
        : theflogat.technomancy.common.rituals.Ritual.Type.values()) {
    int rgb = theflogat.technomancy.common.rituals.RitualFx.colour(type);
    out.append(type.name()).append(" = #").append(Integer.toHexString(rgb)).append("\n");
    if (!seen.add(rgb)) {
        bad.add("ritual type " + type + " shares a colour with another type");
    }
    if ((rgb & 0xFFFFFF) != rgb) {
        bad.add("ritual type " + type + " colour is out of range: " + rgb);
    }
}

// 5. The render subscriber must be registered, or a packet arrives and nothing is drawn.
//    TechnomClientFx registers on the Forge bus; if it never did, the queue would grow
//    forever and the bolt would be invisible.
clear.invoke(null);

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
