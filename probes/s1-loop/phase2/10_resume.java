// Restored machines must also WORK, not just hold their numbers: switch both back to
// ignoring redstone and let the next probe confirm the loop picks up where it stopped.
//@include _snapshot.frag
java.nio.file.Files.writeString(java.nio.file.Path.of("technom-probe-resume.txt"), snap.toString());
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
int F = level.getMinBuildHeight() + 20;
int Z = 5;
theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity dynamo =
        (theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, F, Z));
theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity condenser =
        (theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(4, F, Z));
dynamo.redstone().set(theflogat.technomancy.common.machines.RedstoneMode.NONE);
condenser.setRedstoneMode(theflogat.technomancy.common.machines.RedstoneMode.NONE);
return "resumed: dynamo " + dynamo.redstone().mode() + ", condenser " + condenser.redstoneMode() + "\nPASS\n";
