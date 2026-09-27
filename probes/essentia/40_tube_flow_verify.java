//@wait 45
// Asserts what the live tube run produced: the jar adopted the aspect on offer
// and no essentia was created or destroyed anywhere along the chain.
//
// The wait above is real time, because the transfer is tick-driven: TC4R
// recalculates tube suction every 2 ticks, tubes pull every 5 and our jar pulls
// every 5, and each tube only carries one unit at a time.
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int floor = level.getMinBuildHeight() + 20;
net.minecraft.core.BlockPos source = new net.minecraft.core.BlockPos(2, floor, 2);
net.minecraft.core.BlockPos tubeA = new net.minecraft.core.BlockPos(2, floor + 1, 2);
net.minecraft.core.BlockPos tubeB = new net.minecraft.core.BlockPos(3, floor + 1, 2);
net.minecraft.core.BlockPos ours = new net.minecraft.core.BlockPos(3, floor, 2);

StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
dev.tc4port.thaumcraft.api.aspect.AspectId ignis = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("ignis");

dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity src =
        (dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity) level.getBlockEntity(source);
theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity jar =
        (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) level.getBlockEntity(ours);

int inTubes = 0;
for (net.minecraft.core.BlockPos p : new net.minecraft.core.BlockPos[] {tubeA, tubeB}) {
    Object be = level.getBlockEntity(p);
    if (be instanceof dev.tc4port.thaumcraft.api.essentia.EssentiaTransport t) {
        int held = Math.max(0, t.essentiaAmount(net.minecraft.core.Direction.DOWN));
        inTubes += held;
        out.append("tube ").append(p.toShortString())
           .append(" suction=").append(t.suctionAmount(net.minecraft.core.Direction.WEST))
           .append(" type=").append(t.suctionType(net.minecraft.core.Direction.DOWN))
           .append(" holds=").append(held).append("\n");
    }
}
out.append("source=").append(src.amount()).append(" tubes=").append(inTubes)
   .append(" ours=").append(jar.amount()).append(" aspect=").append(jar.aspect()).append("\n");

int total = src.amount() + inTubes + jar.amount();
out.append("CONSERVATION: ").append(total).append(" of 64\n");
if (total != 64) bad.add("essentia was created or destroyed: " + total + " != 64");

// The whole point of the run: essentia actually moved, through real TC4R tubes.
if (jar.amount() <= 0) bad.add("nothing reached our jar - if tube tick counters are 0 the chunk is not forced");
if (!ignis.equals(jar.aspect())) bad.add("our jar did not adopt ignis: " + jar.aspect());

// Suction must track the contents it now holds: unlabelled base 48, +1 per 50.
int expectedSuction = jar.amount() >= 640 ? 0 : 48 + jar.amount() / 50;
if (jar.suctionAmount(net.minecraft.core.Direction.UP) != expectedSuction) {
    bad.add("suction " + jar.suctionAmount(net.minecraft.core.Direction.UP) + " != " + expectedSuction
            + " at " + jar.amount() + " stored");
}
out.append("suction=").append(jar.suctionAmount(net.minecraft.core.Direction.UP))
   .append(" (expected ").append(expectedSuction).append(")\n");

if (bad.isEmpty()) {
    out.append("PASS\n");
} else {
    out.append("FAIL: ").append(bad).append("\n");
}
return out.toString();
