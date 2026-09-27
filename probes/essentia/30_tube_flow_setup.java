// Builds a real TC4R tube network feeding our jar, then records the starting
// totals so the next probe can assert conservation over the whole run.
//
// The source is an UNLABELLED warded jar on purpose: a labelled one demands 64
// minimum suction and every tube costs a point, so a two-tube run genuinely
// cannot drain it. That is TC4's design, not a defect to work around.
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int floor = level.getMinBuildHeight() + 20;
net.minecraft.core.BlockPos source = new net.minecraft.core.BlockPos(2, floor, 2);
net.minecraft.core.BlockPos tubeA = new net.minecraft.core.BlockPos(2, floor + 1, 2);
net.minecraft.core.BlockPos tubeB = new net.minecraft.core.BlockPos(3, floor + 1, 2);
net.minecraft.core.BlockPos ours = new net.minecraft.core.BlockPos(3, floor, 2);

net.minecraft.world.level.block.Block wardedJar = net.minecraft.core.registries.BuiltInRegistries.BLOCK
        .get(new net.minecraft.resources.ResourceLocation("thaumcraft", "warded_jar"));
net.minecraft.world.level.block.Block tube = net.minecraft.core.registries.BuiltInRegistries.BLOCK
        .get(new net.minecraft.resources.ResourceLocation("thaumcraft", "essentia_tube"));
net.minecraft.world.level.block.Block quantum = net.minecraft.core.registries.BuiltInRegistries.BLOCK
        .get(new net.minecraft.resources.ResourceLocation("technom", "quantum_jar"));

level.setBlockAndUpdate(source, wardedJar.defaultBlockState());
level.setBlockAndUpdate(tubeA, tube.defaultBlockState());
level.setBlockAndUpdate(tubeB, tube.defaultBlockState());
level.setBlockAndUpdate(ours, quantum.defaultBlockState());

StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
dev.tc4port.thaumcraft.api.aspect.AspectId ignis = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("ignis");

dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity src =
        (dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity) level.getBlockEntity(source);
int leftover = src.addToContainer(ignis, 64);
out.append("source warded jar: leftover=").append(leftover).append(" amount=").append(src.amount())
   .append(" filter=").append(src.filter()).append(" minSuction=").append(src.minimumSuction()).append("\n");
if (leftover != 0 || src.amount() != 64) bad.add("could not fill the source jar");
if (src.filter() != null) bad.add("source jar must stay unlabelled for this topology");

theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity jar =
        (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) level.getBlockEntity(ours);
jar.store().clearContents();
jar.store().clearFilters();
out.append("our jar: amount=").append(jar.amount()).append(" suctionUP=").append(jar.suctionAmount(
        net.minecraft.core.Direction.UP)).append("\n");

// TC4R must recognise both ends, or nothing can flow however good our numbers are.
Object fromTube = dev.tc4port.thaumcraft.api.ThaumcraftApiHelper
        .getConnectableTransport(level, tubeB, net.minecraft.core.Direction.DOWN);
Object fromUs = dev.tc4port.thaumcraft.api.ThaumcraftApiHelper
        .getConnectableTransport(level, ours, net.minecraft.core.Direction.UP);
out.append("tube sees below: ").append(fromTube == null ? "NULL" : fromTube.getClass().getSimpleName())
   .append(" | we see above: ").append(fromUs == null ? "NULL" : fromUs.getClass().getSimpleName()).append("\n");
if (!(fromTube instanceof theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity)) {
    bad.add("the tube does not recognise our jar as a connectable transport");
}
if (!(fromUs instanceof dev.tc4port.thaumcraft.api.essentia.EssentiaTransport)) {
    bad.add("we do not recognise the tube above us");
}
out.append("tube states: ").append(level.getBlockState(tubeA)).append(" / ")
   .append(level.getBlockState(tubeB)).append("\n");

out.append("total essentia in the system at start: 64\n");
if (bad.isEmpty()) {
    out.append("PASS\n");
} else {
    out.append("FAIL: ").append(bad).append("\n");
}
return out.toString();
