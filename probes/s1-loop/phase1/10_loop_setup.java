// S1 closed loop: quantum jar (640 potentia) -> 3 TC4R tubes -> boosted essentia dynamo
// -> Forge Energy -> energy condenser -> TC4R warded jar.
//
// Every S1 machine, TC4R's tube network, and an FE transfer between two of our own
// block entities with independent bookkeeping - which is what lets the verify probe
// assert an exact conservation law across the whole chain.
//
// Laid out at z = 5 so it cannot touch the probes/essentia fixtures at z = 2 and (6, 6).
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
level.setChunkForced(0, 0, true);
int F = level.getMinBuildHeight() + 20;
int Z = 5;
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

for (int x = 0; x <= 8; x++) {
    for (int z = Z - 1; z <= Z + 1; z++) {
        for (int y = F - 1; y <= F + 2; y++) {
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }
}

java.util.function.Function<String, net.minecraft.world.level.block.Block> block = id ->
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(id));

net.minecraft.core.BlockPos jarPos = new net.minecraft.core.BlockPos(1, F, Z);
net.minecraft.core.BlockPos dynamoPos = new net.minecraft.core.BlockPos(3, F, Z);
net.minecraft.core.BlockPos condenserPos = new net.minecraft.core.BlockPos(4, F, Z);
net.minecraft.core.BlockPos sinkPos = new net.minecraft.core.BlockPos(4, F - 1, Z);

level.setBlockAndUpdate(jarPos, block.apply("technom:quantum_jar").defaultBlockState());
for (int x = 1; x <= 3; x++) {
    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, F + 1, Z),
            block.apply("thaumcraft:essentia_tube").defaultBlockState());
}
level.setBlockAndUpdate(dynamoPos, block.apply("technom:essentia_dynamo").defaultBlockState());

// The condenser hands potentia DOWN only, into the warded jar below it.
net.minecraft.world.level.block.state.BlockState condenserState =
        block.apply("technom:energy_condenser").defaultBlockState();
for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
    condenserState = condenserState.setValue(
            theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock.output(d),
            d == net.minecraft.core.Direction.DOWN);
}
level.setBlockAndUpdate(condenserPos, condenserState);
level.setBlockAndUpdate(sinkPos, block.apply("thaumcraft:warded_jar").defaultBlockState());

dev.tc4port.thaumcraft.api.aspect.AspectId potentia = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("potentia");

theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity jar =
        (theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity) level.getBlockEntity(jarPos);
int filled = jar.store().add(potentia, 640, false);
if (filled != 640) bad.add("could not fill the quantum jar: " + filled);

theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity dynamo =
        (theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity) level.getBlockEntity(dynamoPos);
dynamo.setFacing(net.minecraft.core.Direction.EAST);
dynamo.setBoosted(true);
if (dynamo.facing() != net.minecraft.core.Direction.EAST) bad.add("dynamo did not face the condenser");
if (!dynamo.isBoosted()) bad.add("potency gem not installed");
if (dynamo.canInputFrom(net.minecraft.core.Direction.EAST)) bad.add("the energy face accepts essentia (A-7)");

theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity condenser =
        (theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity) level.getBlockEntity(condenserPos);

// Wiring: each hop must be recognised by the side that uses it.
Object tubeAboveDynamo = dev.tc4port.thaumcraft.api.ThaumcraftApiHelper
        .getConnectableTransport(level, dynamoPos, net.minecraft.core.Direction.UP);
if (tubeAboveDynamo == null) bad.add("the dynamo does not see the tube above it");
Object tubeAboveJar = dev.tc4port.thaumcraft.api.ThaumcraftApiHelper
        .getConnectableTransport(level, jarPos, net.minecraft.core.Direction.UP);
if (tubeAboveJar == null) bad.add("the jar does not see the tube above it");
Object sinkBelow = dev.tc4port.thaumcraft.api.ThaumcraftApiHelper
        .getConnectableTransport(level, condenserPos, net.minecraft.core.Direction.DOWN);
if (!(sinkBelow instanceof dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity)) {
    bad.add("the condenser does not see the warded jar below it: " + sinkBelow);
}
boolean acceptsFe = condenser.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
        net.minecraft.core.Direction.WEST).map(e -> e.canReceive()).orElse(false);
if (!acceptsFe) bad.add("the condenser does not take FE on the face the dynamo feeds");

long perUnit = theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader.table().energyPerUnit(
        theflogat.technomancy.common.essentia.fuel.FuelEnvironment.of(level, dynamoPos), potentia);
out.append("fuel table: potentia = ").append(perUnit).append(" Q per unit at this position\n");
if (perUnit <= 0) bad.add("the fuel table is not loaded, nothing will burn");

out.append("jar=").append(jar.amount()).append(" ").append(jar.aspect())
   .append(" | dynamo facing=").append(dynamo.facing()).append(" boosted=").append(dynamo.isBoosted())
   .append(" rate=").append(dynamo.ratePerTick()).append(" Q/t, ").append(dynamo.unitsPerCharge()).append(" units/charge")
   .append(" | condenser cost=").append(condenser.costQ()).append(" Q\n");
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
