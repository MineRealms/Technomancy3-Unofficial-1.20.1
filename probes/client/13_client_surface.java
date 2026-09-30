//@wait 1
// The client wiring that nothing else touches. Every item here is client-only setup that a
// dedicated server never runs and GameTest never sees, so a break shows up as a wrong colour or
// a crash the moment a player opens the screen - never as a failed test:
//
//   * CoilClient replaces the original's two ring renderers with block tints: the upper ring is
//     red while a Potency Gem is installed, the lower ring takes the essentia coil's filter
//     colour.
//   * S2MachinesClient tints the purified ores per material - the original averaged the ingot
//     texture's pixels at startup, which cannot run on a server, so the colours are fixed now.
//   * BotaniaClient sets the mana fluid's translucent layer, because a fluid block cannot declare
//     render_type in a model JSON the way the machine blocks do.
//   * ProcessorScreen and RitualTomeScreen are only ever built by the client, so nothing else
//     would notice them throwing in init().
//   * ExistenceHud calls Affinity.byId(...).color() on every HUD frame.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

int white = 0xFFFFFF;
int gemRed = 0xE02020;
net.minecraft.world.level.block.Block itemCoil =
        theflogat.technomancy.common.registry.TechnomBlocks.ITEM_COIL.get();
net.minecraft.world.level.block.Block essentiaCoil =
        theflogat.technomancy.common.registry.TechnomBlocks.ESSENTIA_COIL.get();

// ---- the coil rings ----
for (boolean gem : new boolean[] {false, true}) {
    net.minecraft.world.level.block.state.BlockState state = itemCoil.defaultBlockState()
            .setValue(theflogat.technomancy.common.blocks.coils.CoilBlock.GEM, gem);
    int upper = mc.getBlockColors().getColor(state, null, null, 1) & 0xFFFFFF;
    int lower = mc.getBlockColors().getColor(state, null, null, 0) & 0xFFFFFF;
    int want = gem ? gemRed : white;
    out.append("item_coil gem=").append(gem).append(": upper=0x")
       .append(Integer.toHexString(upper)).append(" lower=0x")
       .append(Integer.toHexString(lower)).append("\n");
    if (upper != want) {
        bad.add("the item coil's upper ring with a gem=" + gem + " is 0x"
                + Integer.toHexString(upper) + ", expected 0x" + Integer.toHexString(want));
    }
    if (lower != white) {
        bad.add("the item coil's lower ring is 0x" + Integer.toHexString(lower)
                + ", expected white - the item coil has no filter");
    }
}
for (boolean gem : new boolean[] {false, true}) {
    net.minecraft.world.level.block.state.BlockState state = essentiaCoil.defaultBlockState()
            .setValue(theflogat.technomancy.common.blocks.coils.CoilBlock.GEM, gem);
    int upper = mc.getBlockColors().getColor(state, null, null, 1) & 0xFFFFFF;
    // With no level to read the filter from, the lower ring has to fall back to white.
    int lower = mc.getBlockColors().getColor(state, null, null, 0) & 0xFFFFFF;
    int want = gem ? gemRed : white;
    out.append("essentia_coil gem=").append(gem).append(": upper=0x")
       .append(Integer.toHexString(upper)).append(" lower=0x")
       .append(Integer.toHexString(lower)).append("\n");
    if (upper != want) {
        bad.add("the essentia coil's upper ring with a gem=" + gem + " is 0x"
                + Integer.toHexString(upper) + ", expected 0x" + Integer.toHexString(want));
    }
    if (lower != white) {
        bad.add("the essentia coil's lower ring with no level is 0x"
                + Integer.toHexString(lower) + ", expected white");
    }
}

// ---- the purified ores, tinted per material ----
java.util.List<net.minecraft.world.item.Item> ores =
        theflogat.technomancy.common.registry.TechnomPureOres.all();
int oreTinted = 0;
for (net.minecraft.world.item.Item item : ores) {
    if (!(item instanceof theflogat.technomancy.common.items.PureOreItem ore)) {
        bad.add("TechnomPureOres.all() returned a non-PureOreItem: " + item);
        continue;
    }
    int want = ore.material().tint() & 0xFFFFFF;
    int got = mc.getItemColors()
            .getColor(new net.minecraft.world.item.ItemStack(item), 0) & 0xFFFFFF;
    if (got != want) {
        bad.add("the purified " + ore.material() + " ore is tinted 0x"
                + Integer.toHexString(got) + ", expected 0x" + Integer.toHexString(want));
    } else {
        oreTinted++;
    }
}
out.append("purified ores tinted per material: ").append(oreTinted).append("/")
   .append(ores.size()).append("\n");
if (ores.isEmpty()) {
    bad.add("TechnomPureOres.all() is empty, so nothing registered the ore tints");
}

// ---- the mana fluid's render layer ----
net.minecraft.world.level.block.Block manaBlock =
        theflogat.technomancy.common.registry.TechnomFluids.MANA_BLOCK.get();
net.minecraftforge.client.ChunkRenderTypeSet manaTypes =
        net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderLayers(manaBlock.defaultBlockState());
boolean manaTranslucent = manaTypes.contains(net.minecraft.client.renderer.RenderType.translucent());
out.append("mana block render layers: ").append(manaTypes)
   .append(" translucent=").append(manaTranslucent).append("\n");
if (!manaTranslucent) {
    bad.add("the mana block does not render translucent, so BotaniaClient's render layer was lost");
}

// ---- the two screens ----
if (mc.player == null) {
    bad.add("no player, so the container screens cannot be built");
} else {
    net.minecraft.world.entity.player.Inventory inventory = mc.player.getInventory();
    theflogat.technomancy.common.menu.ProcessorMenu menu =
            theflogat.technomancy.common.registry.TechnomMenus.PROCESSOR.get().create(1, inventory);
    if (menu == null) {
        bad.add("the processor menu type could not create a menu");
    } else {
        net.minecraft.client.gui.screens.Screen processor =
                new theflogat.technomancy.client.screen.ProcessorScreen(
                        menu, inventory, net.minecraft.network.chat.Component.empty());
        processor.init(mc, 320, 240);
        processor.removed();
        out.append("processor screen built and initialised\n");
    }
}
net.minecraft.client.gui.screens.Screen tome =
        new theflogat.technomancy.client.screen.RitualTomeScreen();
tome.init(mc, 320, 240);
tome.removed();
out.append("ritual tome screen built and initialised\n");

// ---- the affinity ids the existence gauge indexes with ----
java.util.List<String> affinityProblems = new java.util.ArrayList<String>();
for (int id = 0; id <= 5; id++) {
    theflogat.technomancy.common.player.Affinity affinity =
            theflogat.technomancy.common.player.Affinity.byId(id);
    if (affinity == null || affinity.id() != id) {
        affinityProblems.add(id + " -> " + affinity);
    }
}
if (theflogat.technomancy.common.player.Affinity.byId(99)
        != theflogat.technomancy.common.player.Affinity.NORMAL) {
    affinityProblems.add("99 did not fall back to NORMAL");
}
out.append("affinity ids 0..5 resolve: ").append(affinityProblems.isEmpty()).append("\n");
if (!affinityProblems.isEmpty()) {
    bad.add("Affinity.byId is wrong: " + affinityProblems);
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
