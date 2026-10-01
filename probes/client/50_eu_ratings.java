//@wait 15
// Verifies in a live client that every machine owning a MachineEnergy really speaks EU, and that
// each one advertises the rating EuRating derives from its own draw. This is the check the
// server-side tests cannot make: CondenserGtceuChecks covers only the condenser, and EuRatingTest
// proves the derivation, not that the machines call it.
//
// The flower dynamo is the point of the round. Before it, its ports claimed euOut while its ledger
// was built with EnergyLimits.fe, so emitsEu() was false: the output face served an
// IEnergyContainer that refused everything, and because GtceuEnergyProtocol.push answers such a
// face with 0 rather than NOT_APPLICABLE, MachineEnergy.pushOutput never fell back to FE either. A
// GT cable on that face received nothing at all. It must now report a real 32 V output.
//
// Each block is placed next to the player and all six faces are queried; the rating is read as the
// maximum over the faces, because a rotatable machine carries EU only on the face it points at.
// The blocks are placed in the client level only, so the server never sees them and the save is
// untouched; the next chunk update reverts them.
//
// Two traps this probe already fell into once, both worth keeping:
//   * the bridge starts on the integrated server's ServerStartedEvent, which is *before* the client
//     has received its chunks. A probe run too early gets isLoaded() == false, setBlock silently
//     does nothing, and every machine reads as "placed no block entity". Hence the leading wait and
//     the guard below.
//   * GTCEu hangs its own EUToFEProvider$GTEnergyWrapper on every block entity that exposes Forge
//     Energy. On a face where we do not speak EU our container is absent, so the helper hands back
//     that wrapper instead, reporting 0 V and outputsEnergy() == false. It is GT's FE bridge, not a
//     native rating, and counting it would read a consumer's non-EU faces as an EU refusal.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
net.minecraft.client.multiplayer.ClientLevel level = mc.level;
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

if (level == null || mc.player == null) {
    return "FAIL: no client level or player; the world is not joined\n";
}

net.minecraft.core.BlockPos origin = mc.player.blockPosition().above(3);
out.append("player ").append(mc.player.blockPosition()).append(", placing from ").append(origin)
   .append("\n");
if (!level.isLoaded(origin)) {
    return "FAIL: the chunk at " + origin + " is not loaded yet, so setBlock would be a no-op;"
            + " re-run this probe (it is a timing problem, not a defect)\n";
}

long qPerEu = theflogat.technomancy.common.energy.EnergyUnits.qPerEu();
out.append("Q per EU: ").append(qPerEu).append("\n");

// name | expected input V | expected output V. The tiers are the ones EuRatingTest pins, so a
// machine that stops calling EuRating, or calls it with a different draw, fails here.
String[] specs = {
    "essentia_dynamo|0|32",
    "node_dynamo|0|32",
    "flower_dynamo|0|32",
    "biome_morpher|8192|0",
    "electric_bellows|2048|0",
    "eldritch_consumer|131072|0",
    "energy_condenser|2048|0",
    "mana_exchanger|512|0",
    "mana_fabricator|131072|0",
    "node_fabricator|8192|0",
    "existence_dynamic_burner|8192|0",
    "essentia_fusor|131072|0",
    // The static burner takes no energy at all: it must offer no EU container on any face.
    "existence_burner|0|0",
};

for (int i = 0; i < specs.length; i++) {
    String[] parts = specs[i].split("\\|");
    String name = parts[0];
    long wantIn = Long.parseLong(parts[1]);
    long wantOut = Long.parseLong(parts[2]);
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(new net.minecraft.resources.ResourceLocation("technom", name));
    if (block == null) {
        bad.add(name + " is not registered");
        continue;
    }
    net.minecraft.core.BlockPos pos = origin.offset(i % 4 * 3, 0, i / 4 * 3);
    level.setBlock(pos, block.defaultBlockState(), 3);
    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
    if (be == null) {
        bad.add(name + " placed no block entity");
        continue;
    }
    long inV = 0L;
    long inA = 0L;
    long outV = 0L;
    long outA = 0L;
    boolean inFace = false;
    boolean outFace = false;
    int faces = 0;
    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
        com.gregtechceu.gtceu.api.capability.IEnergyContainer container =
                com.gregtechceu.gtceu.api.capability.GTCapabilityHelper
                        .getEnergyContainer(level, pos, side);
        if (container == null
                || container instanceof com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider.GTEnergyWrapper) {
            continue;
        }
        faces++;
        if (container.getInputVoltage() > inV) {
            inV = container.getInputVoltage();
            inA = container.getInputAmperage();
        }
        if (container.getOutputVoltage() > outV) {
            outV = container.getOutputVoltage();
            outA = container.getOutputAmperage();
        }
        inFace |= container.inputsEnergy(side);
        outFace |= container.outputsEnergy(side);
    }
    out.append(String.format("%-28s %-24s in %6dV %dA, out %6dV %dA, %d/6 face(s)%n",
            name, be.getClass().getSimpleName(), inV, inA, outV, outA, faces));

    if (inV != wantIn) {
        bad.add(name + " input voltage is " + inV + "V, expected " + wantIn + "V");
    }
    if (outV != wantOut) {
        bad.add(name + " output voltage is " + outV + "V, expected " + wantOut + "V");
    }
    if (wantIn > 0) {
        if (!inFace) {
            bad.add(name + " never reports inputsEnergy on any face, so GT would route nothing to it");
        }
        if (outV != 0) {
            bad.add(name + " is a consumer but advertises " + outV + "V of EU output");
        }
        if (inA != 2) {
            bad.add(name + " input amperage is " + inA + ", expected 2");
        }
    }
    if (wantOut > 0) {
        if (!outFace) {
            bad.add(name + " never reports outputsEnergy on any face, so a GT cable gets nothing");
        }
        if (inV != 0) {
            bad.add(name + " is a generator but advertises " + inV + "V of EU input");
        }
        if (outA != 2) {
            bad.add(name + " output amperage is " + outA + ", expected 2");
        }
    }
    if (wantIn == 0 && wantOut == 0 && faces != 0) {
        bad.add(name + " takes no energy, so it must not offer an EU container at all");
    }
}

// The flower dynamo is the one machine whose EU face used to be dead, so pin the two readings that
// were wrong rather than only the maximum: its output face must be a real sink for a GT cable.
net.minecraft.world.level.block.Block flower = net.minecraft.core.registries.BuiltInRegistries.BLOCK
        .get(new net.minecraft.resources.ResourceLocation("technom", "flower_dynamo"));
if (flower != null) {
    net.minecraft.core.BlockPos pos = origin.offset(6, 0, 6);
    level.setBlock(pos, flower.defaultBlockState(), 3);
    int live = 0;
    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
        com.gregtechceu.gtceu.api.capability.IEnergyContainer container =
                com.gregtechceu.gtceu.api.capability.GTCapabilityHelper
                        .getEnergyContainer(level, pos, side);
        if (container == null
                || container instanceof com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider.GTEnergyWrapper) {
            continue;
        }
        if (container.outputsEnergy(side) && container.getOutputVoltage() > 0) {
            live++;
        }
    }
    out.append("flower_dynamo output faces offering real EU: ").append(live).append("\n");
    if (live != 1) {
        bad.add("the flower dynamo must offer exactly one live EU output face (its facing), found " + live);
    }
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
