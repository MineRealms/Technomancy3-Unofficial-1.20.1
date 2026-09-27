// Shared fragment (inlined by tools/live_probe.py's //@include): reads every piece of
// persisted state in the S1 loop into `snap`, a key -> value map. Before and after a
// restart the same code runs, so any difference is a persistence defect, not a probe bug.
java.util.Map<String, String> snap = new java.util.LinkedHashMap<String, String>();
{
    net.minecraft.server.level.ServerLevel s_level =
            net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
    s_level.setChunkForced(0, 0, true);
    int s_F = s_level.getMinBuildHeight() + 20;
    int s_Z = 5;
    theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity s_jar =
            (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity)
                    s_level.getBlockEntity(new net.minecraft.core.BlockPos(1, s_F, s_Z));
    snap.put("jar.amount", String.valueOf(s_jar.amount()));
    snap.put("jar.aspect", String.valueOf(s_jar.aspect()));
    snap.put("jar.filter", String.valueOf(s_jar.filter()));
    for (int s_x = 1; s_x <= 3; s_x++) {
        dev.tc4port.thaumcraft.api.essentia.EssentiaTransport s_tube =
                (dev.tc4port.thaumcraft.api.essentia.EssentiaTransport)
                        s_level.getBlockEntity(new net.minecraft.core.BlockPos(s_x, s_F + 1, s_Z));
        snap.put("tube" + s_x, s_tube.essentiaAmount(net.minecraft.core.Direction.DOWN) + " "
                + s_tube.essentiaType(net.minecraft.core.Direction.DOWN));
    }
    theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity s_dynamo =
            (theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity)
                    s_level.getBlockEntity(new net.minecraft.core.BlockPos(3, s_F, s_Z));
    snap.put("dynamo.essentia", s_dynamo.store().total() + " " + s_dynamo.store().dominantAspect());
    snap.put("dynamo.bankedQ", String.valueOf(s_dynamo.fuel()));
    snap.put("dynamo.storedQ", String.valueOf(s_dynamo.energy().ledger().stored()));
    snap.put("dynamo.boosted", String.valueOf(s_dynamo.isBoosted()));
    snap.put("dynamo.facing", String.valueOf(s_dynamo.facing()));
    snap.put("dynamo.redstone", s_dynamo.redstone().mode() + " modified=" + s_dynamo.redstone().isModified());
    net.minecraft.core.BlockPos s_cPos = new net.minecraft.core.BlockPos(4, s_F, s_Z);
    theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity s_condenser =
            (theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity) s_level.getBlockEntity(s_cPos);
    snap.put("condenser.essentia", String.valueOf(s_condenser.amount()));
    snap.put("condenser.storedQ", String.valueOf(s_condenser.energy().ledger().stored()));
    snap.put("condenser.unfinishedQ", String.valueOf(s_condenser.unfinishedQ()));
    snap.put("condenser.redstone", s_condenser.redstoneMode() + " modified=" + s_condenser.isRedstoneModified());
    StringBuilder s_outs = new StringBuilder();
    for (net.minecraft.core.Direction s_d : net.minecraft.core.Direction.values()) {
        if (theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock.outputs(s_level.getBlockState(s_cPos), s_d)) {
            s_outs.append(s_d).append(' ');
        }
    }
    snap.put("condenser.outputs", s_outs.toString().trim());
    dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity s_sink =
            (dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity)
                    s_level.getBlockEntity(new net.minecraft.core.BlockPos(4, s_F - 1, s_Z));
    snap.put("sink", s_sink.amount() + " " + s_sink.aspect());
}
