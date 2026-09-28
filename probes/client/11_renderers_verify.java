//@wait 3
// The second half of 10_renderers_and_tab.java: by now the blocks the integrated server
// placed have arrived on the client, so this is where the client actually has a block
// entity for each and a renderer can be resolved for it. A block entity whose renderer is
// missing does not fail loudly - its dynamic parts just never draw - so nothing but a
// client-side read can catch it.
//
// The origin is the one 10_renderers_and_tab.java actually used, handed over through a
// system property because the two probes share a JVM. It cannot be recomputed: the test
// player is in a void world and falls, so its position moves between the two probes.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null) {
    return "FAIL: the client is not in a world\n";
}
String handedOver = System.getProperty("technom.probe.renderer.origin");
if (handedOver == null) {
    return "FAIL: 10_renderers_and_tab.java did not run first, so the placed blocks "
         + "are not where this probe can find them\n";
}
String[] parts = handedOver.split(",");
net.minecraft.core.BlockPos ORIGIN = new net.minecraft.core.BlockPos(
        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
StringBuilder out = new StringBuilder();
out.append("origin from probe 10 = ").append(ORIGIN.toShortString()).append("\n");
java.util.List<String> bad = new java.util.ArrayList<String>();

java.util.List<net.minecraft.resources.ResourceLocation> ids = new java.util.ArrayList<>();
for (net.minecraft.resources.ResourceLocation id
        : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
    if (id.getNamespace().equals("technom")) {
        ids.add(id);
    }
}
java.util.Collections.sort(ids);

int placed = 0;
int entityBlocks = 0;
int withRenderer = 0;
net.minecraft.world.level.block.entity.BlockEntity jar = null;
int index = 0;
for (net.minecraft.resources.ResourceLocation id : ids) {
    net.minecraft.core.BlockPos at = ORIGIN.offset(index % 8, 0, index / 8);
    index++;
    net.minecraft.world.level.block.Block block =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
    if (!mc.level.getBlockState(at).is(block)) {
        bad.add("the client never saw " + id + " at " + at.toShortString());
        continue;
    }
    placed++;
    if (!(block instanceof net.minecraft.world.level.block.EntityBlock)) {
        continue;
    }
    entityBlocks++;
    net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(at);
    if (be == null) {
        bad.add("no client block entity for " + id);
        continue;
    }
    Object renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
    if (renderer != null) {
        withRenderer++;
    }
    if (be instanceof theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) {
        jar = be;
    }
}
out.append("client saw ").append(placed).append("/").append(ids.size()).append(" block(s)\n");
out.append("block entities: ").append(entityBlocks)
   .append(", with a registered renderer: ").append(withRenderer).append("\n");

// The quantum jar draws its essentia level and label in code, so its renderer is required
// rather than optional.
if (jar == null) {
    bad.add("the quantum jar was not seen on the client, so its renderer could not be checked");
} else if (mc.getBlockEntityRenderDispatcher().getRenderer(jar) == null) {
    bad.add("no BlockEntityRenderer registered for the quantum jar - "
            + "its essentia level and label would never draw");
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
