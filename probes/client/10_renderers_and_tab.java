// Client-side wiring that a server cannot have: the BlockEntityRenderer, the creative
// tab contents, and actually placing our blocks so their client block entities are
// constructed and rendered at least once.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

if (mc.level == null || mc.player == null) {
    return "FAIL: the client is not in a world with a player\n";
}

// Place one of each of our blocks next to the player, on the client level. This is what
// forces a client block entity to be created and a renderer to be resolved for it.
net.minecraft.core.BlockPos origin = mc.player.blockPosition().offset(2, 0, 2);
java.util.List<net.minecraft.core.BlockPos> placed = new java.util.ArrayList<net.minecraft.core.BlockPos>();
int index = 0;
for (net.minecraft.resources.ResourceLocation id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
    if (!id.getNamespace().equals("technom")) {
        continue;
    }
    net.minecraft.core.BlockPos at = origin.offset(index % 8, 0, index / 8);
    index++;
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
    mc.level.setBlock(at, block.defaultBlockState(), 3);
    placed.add(at);
    if (!mc.level.getBlockState(at).is(block)) {
        bad.add("could not place " + id + " on the client level");
        continue;
    }
    net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(at);
    if (block instanceof net.minecraft.world.level.block.EntityBlock) {
        if (be == null) {
            bad.add("no client block entity for " + id);
        } else {
            // A block entity that declares a renderer must actually have one registered,
            // or its dynamic parts silently never draw.
            Object renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
            out.append("  ").append(id.getPath()).append(" BE=").append(be.getClass().getSimpleName())
               .append(" renderer=").append(renderer == null ? "none" : renderer.getClass().getSimpleName())
               .append("\n");
        }
    }
}
out.append("placed ").append(placed.size()).append(" block(s) at ").append(origin.toShortString()).append("\n");

// The quantum jar draws its contents and label in code, so its renderer is required.
net.minecraft.world.level.block.entity.BlockEntity jar = null;
for (net.minecraft.core.BlockPos at : placed) {
    net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(at);
    if (be instanceof theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) {
        jar = be;
    }
}
if (jar == null) {
    bad.add("the quantum jar was not placed, so its renderer could not be checked");
} else {
    Object renderer = mc.getBlockEntityRenderDispatcher().getRenderer(jar);
    if (renderer == null) {
        bad.add("no BlockEntityRenderer registered for the quantum jar - its essentia level and label would never draw");
    } else {
        out.append("quantum jar renderer: ").append(renderer.getClass().getName()).append("\n");
    }
}

// A creative tab that reports nothing hides the whole mod. Contents are built lazily,
// so they have to be rebuilt before being read.
try {
    net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
            net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS, true, mc.level.registryAccess());
} catch (Throwable ignored) {
    // Already built for this session; the read below is what matters.
}
net.minecraft.world.item.CreativeModeTab tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
        .get(new net.minecraft.resources.ResourceLocation("technom", "main"));
if (tab == null) {
    bad.add("our creative tab is not registered");
} else {
    int shown = tab.getDisplayItems().size();
    out.append("creative tab holds ").append(shown).append(" item(s)\n");
    if (shown == 0) {
        bad.add("our creative tab is empty");
    }
}

// Clean up, so a repeated run starts from the same world as the first.
for (net.minecraft.core.BlockPos at : placed) {
    mc.level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
}
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
