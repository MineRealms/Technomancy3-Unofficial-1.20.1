//@wait 1
// Locates the node dynamo nearest the player and any GT battery buffer nearby, then reports the
// energy state on BOTH sides, every neighbour of the dynamo, where the cable run actually is, and
// what the GT energy net thinks the route between them is. Reads the SERVER level: a client-side
// block entity never receives the ledger, so a client read would show 0 Q for a full dynamo.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
net.minecraft.server.MinecraftServer srv = mc.getSingleplayerServer();
if (srv == null) {
    return "FAIL: no integrated server (is a world joined?)\n";
}
net.minecraft.server.level.ServerLevel level = srv.overworld();
net.minecraft.core.BlockPos p = mc.player != null
        ? mc.player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
StringBuilder out = new StringBuilder();
out.append("player ").append(p).append("   paused=").append(mc.isPaused()).append("\n");

java.util.List<net.minecraft.core.BlockPos> dynamos = new java.util.ArrayList<>();
java.util.List<net.minecraft.core.BlockPos> batts = new java.util.ArrayList<>();
int pcx = p.getX() >> 4;
int pcz = p.getZ() >> 4;
for (int cx = pcx - 4; cx <= pcx + 4; cx++) {
    for (int cz = pcz - 4; cz <= pcz + 4; cz++) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) {
            continue;
        }
        for (net.minecraft.world.level.block.entity.BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity) {
                dynamos.add(be.getBlockPos().immutable());
            } else {
                String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(be.getBlockState().getBlock()).getPath();
                if (path.contains("battery")) {
                    batts.add(be.getBlockPos().immutable());
                }
            }
        }
    }
}
out.append("node dynamos in 4 chunks: ").append(dynamos.size())
   .append("   battery-like BEs: ").append(batts.size()).append("\n");

net.minecraft.core.BlockPos dp = null;
double best = Double.MAX_VALUE;
for (net.minecraft.core.BlockPos c : dynamos) {
    double d = c.distSqr(p);
    if (d < best) {
        best = d;
        dp = c;
    }
}
if (dp == null) {
    return out.append("FAIL: no node dynamo within 4 chunks of the player\n").toString();
}
theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity nd =
        (theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity) level.getBlockEntity(dp);
out.append("nearest node dynamo ").append(dp)
   .append("  (").append((int) Math.sqrt(best)).append(" blocks away)\n");
out.append("  facing=").append(nd.facing())
   .append(" vis=").append(nd.vis()).append("/16")
   .append(" fuel=").append(nd.fuel())
   .append(" boosted=").append(nd.isBoosted()).append("\n");
out.append("  stored=").append(nd.energy().ledger().stored())
   .append("/").append(nd.energy().ledger().capacity())
   .append("  ports=").append(nd.energy().ports()).append("\n");
out.append("  blockstate=").append(level.getBlockState(dp)).append("\n");

// Every neighbour: the dynamo emits on its facing face only, so this is where the answer lives.
net.minecraft.core.BlockPos firstCable = null;
for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
    net.minecraft.core.BlockPos n = dp.relative(side);
    net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(n).getBlock());
    boolean cable = id.getPath().contains("wire") || id.getPath().contains("cable");
    out.append("  neighbour ").append(side).append(" -> ").append(n).append(" = ").append(id)
       .append(cable ? "   [CABLE]" : "")
       .append(side == nd.facing() ? "   <-- OUTPUT FACE" : "").append("\n");
    if (cable && firstCable == null) {
        firstCable = n.immutable();
    }
}

// Where the run actually goes: every cable within 8 blocks of the dynamo.
java.util.Map<String, java.util.List<String>> wires = new java.util.TreeMap<>();
for (net.minecraft.core.BlockPos bp : net.minecraft.core.BlockPos.betweenClosed(
        dp.offset(-8, -4, -8), dp.offset(8, 4, 8))) {
    String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(bp).getBlock()).getPath();
    if (path.contains("wire") || path.contains("cable")) {
        wires.computeIfAbsent(path, k -> new java.util.ArrayList<>()).add(bp.toShortString());
    }
}
out.append("  cable run within 8 blocks: ").append(wires).append("\n");

// What the GT energy net thinks: a route whose max loss reaches the packet voltage is dropped.
try {
    com.gregtechceu.gtceu.common.pipelike.cable.LevelEnergyNet lnet =
            com.gregtechceu.gtceu.common.pipelike.cable.LevelEnergyNet.getOrCreate(level);
    out.append("  net from dynamo pos: ")
       .append(lnet.getNetFromPos(dp) == null ? "none (the dynamo is not a cable)" : "present")
       .append("\n");
    if (firstCable != null) {
        com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet en = lnet.getNetFromPos(firstCable);
        out.append("  net from cable ").append(firstCable).append(": ")
           .append(en == null ? "none" : "present").append("\n");
        if (en != null) {
            java.util.List<com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath> routes =
                    en.getNetData(firstCable);
            out.append("  routes out of that cable: ").append(routes.size()).append("\n");
            for (com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath r : routes) {
                out.append("    dist=").append(r.getDistance())
                   .append(" maxLoss=").append(r.getMaxLoss())
                   .append(" target=").append(r.getTargetPipePos())
                   .append(" facing=").append(r.getTargetFacing())
                   .append(" handler=").append(r.getHandler(level)).append("\n");
            }
        }
    }
} catch (Throwable t) {
    out.append("  energy net query threw: ").append(t).append("\n");
}

// Battery buffers: report the GT container on every face of each one found.
for (net.minecraft.core.BlockPos b : batts) {
    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(b).getBlock()).toString();
    out.append("battery ").append(id).append(" at ").append(b)
       .append("  (").append((int) Math.sqrt(b.distSqr(dp))).append(" blocks from dynamo)\n");
    // Reflected, not typed: IMachineBlockEntity drags in LDLib classes the probe compiler cannot
    // see (LDLib is shaded into GTCEu), so naming the interface fails to compile.
    net.minecraft.world.level.block.entity.BlockEntity bbe = level.getBlockEntity(b);
    try {
        Object mm = bbe.getClass().getMethod("getMetaMachine").invoke(bbe);
        Object front = mm.getClass().getMethod("getFrontFacing").invoke(mm);
        out.append("   machine=").append(mm.getClass().getSimpleName())
           .append(" frontFacing=").append(front)
           .append("  blockstate=").append(level.getBlockState(b)).append("\n");
    } catch (Throwable t) {
        out.append("   (front facing unreadable: ").append(t.getClass().getSimpleName()).append(")\n");
    }
    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
        com.gregtechceu.gtceu.api.capability.IEnergyContainer c =
                com.gregtechceu.gtceu.api.capability.GTCapabilityHelper.getEnergyContainer(level, b, side);
        if (c == null) {
            continue;
        }
        out.append("   face ").append(side)
           .append(" stored=").append(c.getEnergyStored()).append("/").append(c.getEnergyCapacity())
           .append(" inV=").append(c.getInputVoltage()).append(" inA=").append(c.getInputAmperage())
           .append(" outV=").append(c.getOutputVoltage())
           .append(" accepts=").append(c.inputsEnergy(side)).append("\n");
    }
}
return out.toString();
