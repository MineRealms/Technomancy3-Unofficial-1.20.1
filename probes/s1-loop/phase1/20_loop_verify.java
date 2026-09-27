//@wait 60
// After a minute of running, the loop's books must balance EXACTLY across two machines
// that keep them independently:
//
//   units that left the essentia side x Q per unit
//     == dynamo banked Q + dynamo stored Q
//      + condenser stored Q + units made x cost + condenser unfinished Q
//
// The left side is counted in essentia (jar, tubes, dynamo cache); the right side in
// energy. Nothing links the two except the machines doing their jobs correctly, so a
// duplication or deletion anywhere - a tube, the fuel bank, the FE hand-over, the
// condenser's progress - breaks the equality.
//@include _snapshot.frag
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int F = level.getMinBuildHeight() + 20;
int Z = 5;
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
dev.tc4port.thaumcraft.api.aspect.AspectId potentia = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("potentia");

theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity jar =
        (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(1, F, Z));
net.minecraft.core.BlockPos dynamoPos = new net.minecraft.core.BlockPos(3, F, Z);
theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity dynamo =
        (theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity) level.getBlockEntity(dynamoPos);
theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity condenser =
        (theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(4, F, Z));
dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity sink =
        (dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(4, F - 1, Z));

int inTubes = 0;
for (int x = 1; x <= 3; x++) {
    dev.tc4port.thaumcraft.api.essentia.EssentiaTransport tube = (dev.tc4port.thaumcraft.api.essentia.EssentiaTransport)
            level.getBlockEntity(new net.minecraft.core.BlockPos(x, F + 1, Z));
    inTubes += Math.max(0, tube.essentiaAmount(net.minecraft.core.Direction.DOWN));
}
int burned = 640 - jar.amount() - inTubes - dynamo.store().total();
int made = sink.amount() + condenser.amount();
long perUnit = theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader.table().energyPerUnit(
        theflogat.technomancy.common.essentia.fuel.FuelEnvironment.of(level, dynamoPos), potentia);

long left = burned * perUnit;
long right = dynamo.fuel() + dynamo.energy().ledger().stored()
        + condenser.energy().ledger().stored() + made * condenser.costQ() + condenser.unfinishedQ();

out.append("state: ").append(snap).append("\n");
out.append("essentia: jar ").append(jar.amount()).append(" + tubes ").append(inTubes)
   .append(" + dynamo cache ").append(dynamo.store().total()).append(" + burned ").append(burned).append(" = 640\n");
out.append("energy:   ").append(burned).append(" x ").append(perUnit).append(" = ").append(left)
   .append("  vs  banked ").append(dynamo.fuel()).append(" + dynamo ").append(dynamo.energy().ledger().stored())
   .append(" + condenser ").append(condenser.energy().ledger().stored())
   .append(" + ").append(made).append(" x ").append(condenser.costQ())
   .append(" + unfinished ").append(condenser.unfinishedQ()).append(" = ").append(right).append("\n");

if (burned <= 0) bad.add("the dynamo burned nothing - check the chunk is forced and the tubes connect");
if (left != right) bad.add("energy books do not balance: " + left + " != " + right + " (diff " + (left - right) + ")");
if (made >= burned && burned > 0) bad.add("the loop is not a net sink: made " + made + " from " + burned);
if (made > 0 && !potentia.equals(sink.aspect()) && sink.amount() > 0) bad.add("the sink holds " + sink.aspect());
if (condenser.energy().ledger().stored() + condenser.unfinishedQ() + made * condenser.costQ() <= 0) {
    bad.add("no energy ever reached the condenser");
}
out.append("net: ").append(burned).append(" potentia burned -> ").append(made)
   .append(" potentia made (the condenser is meant to be a sink)\n");
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
