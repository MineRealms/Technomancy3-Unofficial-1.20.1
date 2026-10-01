// Client-side wiring that a server cannot have: the creative tab contents, and actually
// placing our blocks so their client block entities are constructed and a renderer is
// resolved for each.
//
// The placing has to happen on the integrated SERVER, not on mc.level. A ClientLevel's
// chunk refuses setBlockState (the client is not allowed to author blocks), so the old
// version of this probe - mc.level.setBlock(...) followed by an immediate read-back -
// could never pass: every one of the 44 blocks "failed to place" for that reason alone.
// Placing server-side also means the client only sees the blocks once the block-update
// packets arrive, which is what 11_renderers_verify.java waits for.
//
// The placement is scheduled and NOT waited for. Blocking the client thread here would
// stall the integrated server's own thread, which is exactly what the first attempt at
// this did - it timed out with the blocks still unplaced.
//
// Both probes share a JVM, so the origin is handed over through a system property rather
// than recomputed. Recomputing does not work: the test player is in a void world and
// falls, so "the player's position" is a different place by the time 11 runs.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null || mc.player == null) {
    return "FAIL: the client is not in a world with a player\n";
}
net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
if (server == null) {
    return "FAIL: no integrated server, so blocks cannot be placed\n";
}
net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = mc.level.dimension();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

// The creative tab is a pure client thing, but it cannot be read straight from here. A tab's
// display items are only filled in by CreativeModeTabs.tryRebuildTabContents, which vanilla calls
// from CreativeModeInventoryScreen and nowhere else, and an unbuilt tab returns an EMPTY
// collection rather than throwing - so reading it from the connection thread silently reported
// "no technom item is in any creative tab", which looks exactly like a regression. Rebuild first,
// on the client thread, then read.
//
// hasPermissions is true so the rebuild is not skipped for a non-op; every technom item is
// reachable without permissions anyway, so it cannot mask a missing one.
java.util.Set<String> shown = new java.util.HashSet<String>();
java.util.concurrent.atomic.AtomicReference<String> tabFailure =
        new java.util.concurrent.atomic.AtomicReference<String>();
java.util.concurrent.CountDownLatch tabsDone = new java.util.concurrent.CountDownLatch(1);
mc.execute(() -> {
    try {
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                mc.level.enabledFeatures(), true, mc.level.registryAccess());
        for (net.minecraft.world.item.CreativeModeTab tab
                : net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB) {
            for (net.minecraft.world.item.ItemStack stack : tab.getDisplayItems()) {
                net.minecraft.resources.ResourceLocation key =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (key.getNamespace().equals("technom")) {
                    shown.add(key.getPath());
                }
            }
        }
    } catch (Throwable thrown) {
        tabFailure.set(String.valueOf(thrown));
    } finally {
        tabsDone.countDown();
    }
});
if (!tabsDone.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
    return "FAIL: the client thread did not rebuild the creative tabs within 60s\n";
}
if (tabFailure.get() != null) {
    return "FAIL: rebuilding the creative tabs threw " + tabFailure.get() + "\n";
}

// Every one of our blocks, in a stable order so both probes agree on the layout.
java.util.List<net.minecraft.resources.ResourceLocation> ids = new java.util.ArrayList<>();
for (net.minecraft.resources.ResourceLocation id
        : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
    if (id.getNamespace().equals("technom")) {
        ids.add(id);
    }
}
java.util.Collections.sort(ids);
out.append("blocks=").append(ids.size()).append("\n");

server.execute(() -> {
    net.minecraft.server.level.ServerLevel level = server.getLevel(dimension);
    if (level == null) {
        return;
    }
    // Anchor on the server player so the chunk is loaded on the client - a block the
    // client cannot see is indistinguishable from one that never arrived. Gravity is
    // switched off so the player stops falling and the chunk stays put while 11 runs.
    // The old value is stashed because NoGravity is saved in the player NBT: leaving it
    // set would float whoever plays this save afterwards. 11 restores it.
    java.util.List<net.minecraft.server.level.ServerPlayer> players = level.players();
    net.minecraft.core.BlockPos origin;
    if (players.isEmpty()) {
        origin = new net.minecraft.core.BlockPos(0, 100, 0);
    } else {
        net.minecraft.server.level.ServerPlayer player = players.get(0);
        System.setProperty("technom.probe.gravity.was", Boolean.toString(player.isNoGravity()));
        player.setNoGravity(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.hurtMarked = true;
        origin = player.blockPosition().offset(2, 3, 2);
    }
    int index = 0;
    for (net.minecraft.resources.ResourceLocation id : ids) {
        net.minecraft.world.level.block.Block block =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
        level.setBlock(origin.offset(index % 8, 0, index / 8), block.defaultBlockState(), 3);
        index++;
    }
    System.setProperty("technom.probe.renderer.origin",
            origin.getX() + "," + origin.getY() + "," + origin.getZ());
});
out.append("asked the integrated server to place ").append(ids.size())
   .append(" block(s); 11_renderers_verify.java checks them once they arrive\n");
// The tab is filled from the item registry, so the property worth asserting is not "some item is
// in a tab" but "no registered item is missing from every tab" - an item that is in none is one a
// player can only get with /give.
java.util.List<String> unreachable = new java.util.ArrayList<String>();
int itemCount = 0;
for (net.minecraft.resources.ResourceLocation id
        : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
    if (!id.getNamespace().equals("technom")) {
        continue;
    }
    itemCount++;
    if (!shown.contains(id.getPath())) {
        unreachable.add(id.getPath());
    }
}
out.append("creative tabs hold ").append(shown.size()).append(" technom item(s) of ")
   .append(itemCount).append(" registered\n");
if (unreachable.size() > 0) {
    bad.add("no creative tab shows " + unreachable);
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
