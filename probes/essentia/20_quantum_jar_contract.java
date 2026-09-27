// The quantum jar's synchronous contract: suction formula, capacity, the
// all-or-nothing drain, face rules, filters and the dropped-item round trip.
// Nothing here depends on ticks, so it can run immediately after the area prep.
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int floor = level.getMinBuildHeight() + 20;
net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(6, floor, 6);
level.setBlockAndUpdate(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK
        .get(new net.minecraft.resources.ResourceLocation("technom", "quantum_jar")).defaultBlockState());

theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity jar =
        (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) level.getBlockEntity(pos);
dev.tc4port.thaumcraft.api.aspect.AspectId ignis = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("ignis");
dev.tc4port.thaumcraft.api.aspect.AspectId aqua = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("aqua");
dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode EXEC =
        dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE;
dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode SIM =
        dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.SIMULATE;
net.minecraft.core.Direction UP = net.minecraft.core.Direction.UP;

StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

// Suction: unlabelled base 48, +1 per 50 stored, 0 when full while the minimum
// keeps its value - TC4R's WardedJarBlockEntity draws the same distinction
// between "how much do I want" and "how hard is it to take from me".
int[][] expected = {{0, 48, 48, 0}, {49, 48, 48, 2}, {50, 49, 49, 2}, {100, 50, 50, 3},
        {320, 54, 54, 8}, {639, 60, 60, 14}, {640, 0, 60, 15}};
for (int[] row : expected) {
    jar.store().clearContents();
    jar.store().add(ignis, row[0], false);
    int suction = jar.suctionAmount(UP);
    int minimum = jar.minimumSuction();
    int comparator = jar.comparatorOutput();
    out.append("  stored=").append(row[0]).append(" suction=").append(suction)
       .append(" min=").append(minimum).append(" comparator=").append(comparator).append("\n");
    if (suction != row[1]) bad.add("suction at " + row[0] + ": " + suction + " != " + row[1]);
    if (minimum != row[2]) bad.add("minimum at " + row[0] + ": " + minimum + " != " + row[2]);
    if (comparator != row[3]) bad.add("comparator at " + row[0] + ": " + comparator + " != " + row[3]);
}

// Labelled beats a labelled warded jar's 64, which is the point of the deviation.
jar.store().clearContents();
jar.store().add(ignis, 100, false);
jar.setFilter(ignis);
int labelled = jar.suctionAmount(UP);
out.append("labelled at 100 stored: suction=").append(labelled).append(" (warded jar is 64)\n");
if (labelled != 66) bad.add("labelled suction " + labelled + " != 66");
if (labelled <= 64) bad.add("labelled suction does not beat a warded jar");

// Removing the label keeps the remembered aspect (TC4 semantics), clearing the
// contents drops it (the A-2 fix: the original left a phantom type behind).
if (!jar.clearFilter()) bad.add("clearFilter reported no change");
if (jar.aspect() == null) bad.add("clearFilter forgot the remembered aspect");
jar.store().clearContents();
if (jar.aspect() != null) bad.add("clearContents left a phantom aspect: " + jar.aspect());
out.append("filter semantics: label removed keeps aspect, clearing contents drops it\n");

// Capacity is a hard bound, and a second aspect is refused.
jar.store().clearFilters();
int accepted = jar.store().add(ignis, 10_000, false);
if (accepted != 640 || jar.amount() != 640) bad.add("capacity bound: accepted=" + accepted + " stored=" + jar.amount());
if (jar.addEssentia(ignis, 100, UP, EXEC) != 0) bad.add("accepted essentia while full");
if (jar.addEssentia(aqua, 10, UP, EXEC) != 0) bad.add("accepted a second aspect");
out.append("capacity: add(10000) accepted ").append(accepted).append(", full and single-aspect enforced\n");

// EssentiaSource is all-or-nothing, and simulation has no side effects.
if (jar.extractEssentia(ignis, 641, EXEC) != 0) bad.add("over-extraction was allowed");
if (jar.amount() != 640) bad.add("failed extraction still mutated the store");
if (jar.extractEssentia(ignis, 640, SIM) != 640) bad.add("simulated full extraction refused");
if (jar.amount() != 640) bad.add("simulation mutated the store");
out.append("all-or-nothing drain and pure simulation hold\n");

// Faces, measured on an EMPTY jar so "full" cannot confound the result.
jar.store().clearContents();
for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
    boolean isUp = d == UP;
    if (jar.canInputFrom(d) != isUp) bad.add("canInputFrom(" + d + ")");
    if (jar.canOutputTo(d) != isUp) bad.add("canOutputTo(" + d + ")");
    if (jar.isConnectable(d) != isUp) bad.add("isConnectable(" + d + ")");
    int add = jar.addEssentia(ignis, 5, d, SIM);
    if ((add > 0) != isUp) bad.add("addEssentia(" + d + ")=" + add);
}
if (jar.amount() != 0) bad.add("face simulation stored essentia");
out.append("faces: only UP transfers, on an empty jar\n");

// An unregistered aspect must be refused rather than stored under a bogus id.
if (jar.addEssentia(dev.tc4port.thaumcraft.api.aspect.AspectId.parse("technom:not_a_real_aspect"),
        5, UP, EXEC) != 0 || jar.amount() != 0) {
    bad.add("an unregistered aspect was accepted");
}
out.append("unregistered aspects refused\n");

// The dropped item must carry contents, label and schema version, or breaking
// the block silently destroys essentia.
jar.store().add(ignis, 321, false);
jar.setFilter(ignis);
java.util.List<net.minecraft.world.item.ItemStack> drops =
        net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, jar);
String tag = drops.size() == 1 ? String.valueOf(drops.get(0).getTag()) : "<" + drops.size() + " drops>";
out.append("drop tag: ").append(tag).append("\n");
if (drops.size() != 1) bad.add("expected exactly one drop, got " + drops.size());
if (!tag.contains("n:321") || !tag.contains("ignis") || !tag.contains("v:1")) {
    bad.add("drop lost contents, label or schema version");
}

level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
if (bad.isEmpty()) {
    out.append("PASS\n");
} else {
    out.append("FAIL: ").append(bad).append("\n");
}
return out.toString();
