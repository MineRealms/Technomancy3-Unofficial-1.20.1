// The node-creation bolt and the ritual effects are the two things a player does that
// previously produced no visible feedback at all. A server cannot prove either: particles
// are client-side and the bolt is geometry only a client draws. So this drives the client
// half directly and checks the shapes a render would actually consume.
//
// Everything below runs on the CLIENT THREAD. The bridge executes a probe body on its own
// connection thread, and TechnomClientFx's bolt queue is only ever touched from the client
// thread in production - the packet handler goes through NetworkEvent.Context.enqueueWork.
// Writing it from the connection thread while the render thread was iterating it threw
// ConcurrentModificationException out of the render event and killed the client, which is
// what happened the first time this probe was run in place. So the body is handed to
// Minecraft.execute and waited on instead.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null) {
    return "FAIL: the client is not in a world\n";
}

java.lang.reflect.Method accept = theflogat.technomancy.client.fx.TechnomClientFx.class
        .getMethod("accept", byte.class, net.minecraft.core.BlockPos.class, int.class);
java.lang.reflect.Method count = theflogat.technomancy.client.fx.TechnomClientFx.class
        .getMethod("activeBolts");
java.lang.reflect.Method clear = theflogat.technomancy.client.fx.TechnomClientFx.class
        .getMethod("clear");

java.util.concurrent.atomic.AtomicReference<String> answer =
        new java.util.concurrent.atomic.AtomicReference<String>();
java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
mc.execute(() -> {
    StringBuilder out = new StringBuilder();
    java.util.List<String> bad = new java.util.ArrayList<String>();
    try {
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

        // 3. Repeating the same creation must queue the same number of bolts. The geometry
        //    itself is seeded from the position rather than a random source, so two clients
        //    agree; nothing here can read that geometry back, so the queue size is the
        //    observable part.
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

        // 5. Leave the queue empty, so the bolts this probe invented are not left on screen.
        clear.invoke(null);
        if ((int) count.invoke(null) != 0) {
            bad.add("clear() left bolts queued");
        }
    } catch (Throwable thrown) {
        bad.add("the probe threw " + thrown);
    }
    out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
    answer.set(out.toString());
    finished.countDown();
});

if (!finished.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
    return "FAIL: the client thread did not run the probe within 60s\n";
}
return answer.get();
