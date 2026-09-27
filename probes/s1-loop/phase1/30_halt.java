// Halts both machines with redstone (HIGH with no signal) so the state can come to rest
// before the restart. A persistence comparison is only meaningful for a state that is not
// still changing; the next two probes prove it has stopped before recording it.
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int F = level.getMinBuildHeight() + 20;
int Z = 5;
theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity dynamo =
        (theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, F, Z));
theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity condenser =
        (theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(4, F, Z));
dynamo.redstone().set(theflogat.technomancy.common.machines.RedstoneMode.HIGH);
condenser.setRedstoneMode(theflogat.technomancy.common.machines.RedstoneMode.HIGH);
boolean powered = level.hasNeighborSignal(new net.minecraft.core.BlockPos(3, F, Z))
        || level.hasNeighborSignal(new net.minecraft.core.BlockPos(4, F, Z));
return "dynamo " + dynamo.redstone().mode() + ", condenser " + condenser.redstoneMode()
        + ", powered=" + powered + "\n" + (powered ? "FAIL: something powers the machines" : "PASS") + "\n";
