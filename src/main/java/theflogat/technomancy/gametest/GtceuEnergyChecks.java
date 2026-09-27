package theflogat.technomancy.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.utils.GTUtil;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyLedger;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.EnergyProtocolExtension;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.compat.gtceu.EuTier;
import theflogat.technomancy.compat.gtceu.GtceuEnergyProtocol;

/**
 * The GTCEu half of {@link GtceuEnergyGameTests}, split off so the test holder itself carries no
 * GT type and can run on a runtime without GTCEu. Nothing here is reachable unless
 * {@code GtceuPresence.isLoaded()} was true.
 *
 * <p>The port has no machine block yet, so these checks hang a {@link MachineEnergy} on a vanilla
 * barrel: the component only needs its owner for the level, the position and the {@code setChanged}
 * hook, and every EU path under test is reached either through the component's own capability
 * lookup or through a real GT block entity next to it.</p>
 */
final class GtceuEnergyChecks {

    private static final long CAPACITY = 100_000;
    private static final long INPUT_AMPS = 2;
    private static final long OUTPUT_AMPS = 3;

    private GtceuEnergyChecks() {}

    static void tierTable(GameTestHelper helper) {
        helper.assertTrue(EuTier.count() == GTValues.V.length,
                "tier count " + EuTier.count() + " but GTValues.V has " + GTValues.V.length + " entries");
        helper.assertTrue(EuTier.count() == GTValues.VN.length,
                "tier count " + EuTier.count() + " but GTValues.VN has " + GTValues.VN.length + " entries");
        helper.assertTrue(EuTier.count() == GTValues.TIER_COUNT,
                "tier count " + EuTier.count() + " but GTValues.TIER_COUNT is " + GTValues.TIER_COUNT);
        for (EuTier tier : EuTier.values()) {
            helper.assertTrue(tier.voltage() == GTValues.V[tier.index()],
                    tier + " is rated " + tier.voltage() + " EU here but " + GTValues.V[tier.index()] + " in GTCEu");
            helper.assertTrue(tier.name().equals(GTValues.VN[tier.index()]),
                    "tier " + tier.index() + " is " + tier.name() + " here and " + GTValues.VN[tier.index()]
                            + " in GTCEu");
        }
        helper.assertTrue(EuTier.ULV.index() == GTValues.ULV && EuTier.LV.index() == GTValues.LV
                        && EuTier.MV.index() == GTValues.MV && EuTier.MAX.index() == GTValues.MAX,
                "tier indices do not line up with the GTValues constants");

        // Our lookup must round the same way GT's does, or a machine rated from a raw voltage
        // would silently sit one tier away from the cables feeding it.
        long[] probes = {0, 1, 8, 9, 31, 32, 33, 128, 129, 2048, 131_072, 524_289,
                2_147_483_647L, 2_147_483_648L, Long.MAX_VALUE};
        for (long voltage : probes) {
            int ours = EuTier.forVoltage(voltage).index();
            int theirs = GTUtil.getTierByVoltage(voltage);
            helper.assertTrue(ours == theirs,
                    "voltage " + voltage + " is tier " + ours + " here and " + theirs + " in GTCEu");
        }
        Technomancy.LOGGER.info("GameTest EU tiers: {} tiers match GTValues.V/VN, {} voltage probes match"
                + " GTUtil.getTierByVoltage", EuTier.count(), probes.length);
        helper.succeed();
    }

    static void capabilityView(GameTestHelper helper) {
        long rate = EnergyUnits.qPerEu();
        long inVoltage = EuTier.LV.voltage();
        long outVoltage = EuTier.MV.voltage();
        BlockEntity host = host(helper, new BlockPos(1, 1, 1));

        MachineEnergy energy = machine(host);
        IEnergyContainer in = container(energy, Direction.NORTH);
        IEnergyContainer out = container(energy, Direction.SOUTH);
        helper.assertTrue(in != null,
                "no EU container on the EU input face; the GTCEu bootstrap did not register the protocol");
        helper.assertTrue(out != null, "no EU container on the EU output face");
        helper.assertTrue(container(energy, Direction.EAST) == null,
                "a face that carries no EU must stay empty instead of shadowing GTCEu's own FE bridge");
        IEnergyContainer unsided = container(energy, null);
        helper.assertTrue(unsided != null, "probes and TOP ask for the unsided container");

        helper.assertTrue(in.inputsEnergy(Direction.NORTH) && !in.outputsEnergy(Direction.NORTH),
                "the input face reports the wrong direction rights");
        helper.assertTrue(in.inputsEnergy(null), "GT passes null for 'no side restriction'");
        helper.assertTrue(!in.inputsEnergy(Direction.SOUTH), "a face view must not answer for another face");
        helper.assertTrue(in.getInputVoltage() == inVoltage && in.getInputAmperage() == INPUT_AMPS,
                "input rating is " + in.getInputVoltage() + "V " + in.getInputAmperage() + "A");
        helper.assertTrue(in.getOutputVoltage() == 0 && in.getOutputAmperage() == 0,
                "an input-only face must not advertise an output rating");
        helper.assertTrue(out.outputsEnergy(Direction.SOUTH) && !out.inputsEnergy(Direction.SOUTH),
                "the output face reports the wrong direction rights");
        helper.assertTrue(out.getOutputVoltage() == outVoltage && out.getOutputAmperage() == OUTPUT_AMPS,
                "output rating is " + out.getOutputVoltage() + "V " + out.getOutputAmperage() + "A");
        helper.assertTrue(!unsided.inputsEnergy(Direction.NORTH) && !unsided.outputsEnergy(Direction.SOUTH)
                        && !unsided.inputsEnergy(null),
                "a null side must not gain transfer rights");

        helper.assertTrue(in.getEnergyCapacity() == CAPACITY / rate,
                "capacity reads " + in.getEnergyCapacity() + " EU for " + CAPACITY + " Q at " + rate + " Q/EU");
        helper.assertTrue(in.getEnergyCanBeInserted() == CAPACITY / rate,
                "an empty machine must report room, or GT senders skip it entirely");
        helper.assertTrue(in.getEnergyStored() == 0, "a fresh ledger is not empty");

        // Over-voltage first: it must cost nothing, including this tick's ampere quota.
        helper.assertTrue(in.acceptEnergyFromNetwork(Direction.NORTH, outVoltage, 1) == 0,
                "an over-voltage packet must be refused outright, never truncated");
        helper.assertTrue(energy.ledger().stored() == 0, "a refused packet still moved energy");
        helper.assertTrue(in.acceptEnergyFromNetwork(Direction.NORTH, inVoltage, 5) == INPUT_AMPS,
                "the rated ampere limit must cap the accepted packets");
        long stored = INPUT_AMPS * inVoltage * rate;
        helper.assertTrue(energy.ledger().stored() == stored,
                "accepted " + INPUT_AMPS + " packets but the ledger holds " + energy.ledger().stored() + " Q");
        helper.assertTrue(in.getEnergyStored() == INPUT_AMPS * inVoltage,
                "EU reading is " + in.getEnergyStored() + " after " + INPUT_AMPS + " packets");
        helper.assertTrue(in.acceptEnergyFromNetwork(Direction.NORTH, inVoltage, 1) == 0,
                "the ampere quota for this tick is spent and must not be exceeded");
        helper.assertTrue(in.changeEnergy(1000) == 0 && in.addEnergy(1000) == 0 && in.removeEnergy(1000) == 0,
                "changeEnergy must not be an unmetered entry point into the ledger");
        helper.assertTrue(energy.ledger().stored() == stored, "changeEnergy altered the balance");

        // Same offer, but made by real GTCEu code: EnergyContainerList forwards with a null side.
        MachineEnergy driven = machine(host);
        EnergyContainerList list = new EnergyContainerList(List.of(container(driven, Direction.NORTH)));
        helper.assertTrue(list.getInputVoltage() == inVoltage && list.getInputAmperage() == INPUT_AMPS,
                "GT read our rating as " + list.getInputVoltage() + "V " + list.getInputAmperage() + "A");
        helper.assertTrue(list.acceptEnergyFromNetwork(Direction.NORTH, inVoltage, 4) == INPUT_AMPS,
                "GTCEu's own container list could not deliver packets to our view");
        helper.assertTrue(driven.ledger().stored() == stored,
                "GT delivered " + driven.ledger().stored() + " Q instead of " + stored);

        // A fresh machine, so the refusal below cannot be mistaken for a spent quota.
        MachineEnergy probeOnly = machine(host);
        IEnergyContainer probe = container(probeOnly, null);
        helper.assertTrue(probe.acceptEnergyFromNetwork(Direction.NORTH, inVoltage, 1) == 0,
                "an unsided view must not transfer even when it is handed a face");
        helper.assertTrue(probe.acceptEnergyFromNetwork(null, inVoltage, 1) == 0,
                "an unsided view must not transfer unsided either");
        helper.assertTrue(probeOnly.ledger().stored() == 0, "the unsided view moved energy");
        probeOnly.ledger().generate(rate);
        helper.assertTrue(probe.getEnergyStored() == 1, "the unsided view must still read the balance");

        Technomancy.LOGGER.info("GameTest EU views: face rules, packet limits, over-voltage refusal and the"
                + " null-side rule hold through the real IEnergyContainer interface at {} Q per EU", rate);
        helper.succeed();
    }

    static void exchangeWithRealMachine(GameTestHelper helper) {
        long rate = EnergyUnits.qPerEu();
        long voltage = EuTier.LV.voltage();
        ServerLevel level = helper.getLevel();

        MachineDefinition furnace = GTMachines.ELECTRIC_FURNACE[GTValues.LV];
        helper.assertTrue(furnace != null, "GTCEu has no LV electric furnace to exchange energy with");

        // Absolute positions, because the port masks are absolute world directions while the
        // GameTest structure may be placed at any rotation.
        BlockPos machinePos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos hostPos = machinePos.east();
        BlockPos plainPos = hostPos.east();
        level.setBlockAndUpdate(machinePos, furnace.getBlock().defaultBlockState());
        level.setBlockAndUpdate(hostPos, Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(plainPos, Blocks.BARREL.defaultBlockState());

        BlockEntity host = level.getBlockEntity(hostPos);
        BlockEntity machineEntity = level.getBlockEntity(machinePos);
        BlockEntity plainEntity = level.getBlockEntity(plainPos);
        helper.assertTrue(host != null && machineEntity != null && plainEntity != null,
                "one of the test block entities was not created");

        IEnergyContainer receiver = GTCapabilityHelper.getEnergyContainer(level, machinePos, Direction.EAST);
        helper.assertTrue(receiver != null, "the GT machine exposes no energy container on the face we touch");
        helper.assertTrue(receiver.inputsEnergy(Direction.EAST), "the GT machine refuses input on that face");
        helper.assertTrue(receiver.getEnergyStored() == 0, "the GT machine did not start empty");

        // Two rated amperes against a machine that takes one per tick, so the push has to commit
        // exactly what GT reports and refund the rest.
        EnergyLimits limits = EnergyLimits.fe(CAPACITY, 0, CAPACITY).withEuOutput(voltage, 2);
        MachineEnergy energy = new MachineEnergy(host, limits,
                EnergyPorts.generator(EnergyPorts.mask(Direction.WEST)));
        energy.ledger().generate(CAPACITY);

        long sent = energy.pushOutput();
        helper.assertTrue(sent == voltage * rate,
                "pushed " + sent + " Q, expected one " + voltage + " EU packet = " + voltage * rate + " Q");
        helper.assertTrue(receiver.getEnergyStored() == voltage,
                "the GT machine holds " + receiver.getEnergyStored() + " EU, expected " + voltage);
        helper.assertTrue(energy.ledger().stored() == CAPACITY - voltage * rate,
                "the ledger holds " + energy.ledger().stored() + " Q after sending one packet");

        try (EnergyLedger.Reservation left = energy.ledger().reservePackets(rate, level.getGameTime())) {
            helper.assertTrue(left.amps() == 1,
                    "the ampere GT did not take was not refunded; " + left.amps() + " left of 2");
        }

        long again = energy.pushOutput();
        helper.assertTrue(again == 0, "the GT machine took " + again + " Q more in the same tick");
        helper.assertTrue(energy.ledger().stored() == CAPACITY - voltage * rate,
                "a refused push changed the balance");
        helper.assertTrue(receiver.getEnergyStored() == voltage, "a refused push still reached the GT machine");

        // The two refusal kinds must stay distinguishable: only a neighbour that does not speak EU
        // may fall through to Forge Energy. The face is ours, so the neighbour is queried with its
        // own opposite face, exactly as GT senders do.
        GtceuEnergyProtocol protocol = new GtceuEnergyProtocol(energy);
        long time = level.getGameTime();
        helper.assertTrue(protocol.push(plainEntity, Direction.EAST, time) == EnergyProtocolExtension.NOT_APPLICABLE,
                "a neighbour without an EU container must report NOT_APPLICABLE so FE can be tried");
        helper.assertTrue(protocol.push(machineEntity, Direction.WEST, time) == 0,
                "a GT machine that refuses must yield 0, never NOT_APPLICABLE, or FE would bypass its limits");
        helper.assertTrue(energy.ledger().stored() == CAPACITY - voltage * rate,
                "a refusal moved energy after all");

        Technomancy.LOGGER.info("GameTest EU exchange: LV electric furnace accepted 1 A of {} EU ({} Q) from our"
                + " push, the unused ampere was refunded, and refusal stayed distinguishable from 'no EU here'",
                voltage, voltage * rate);
        helper.succeed();
    }

    private static MachineEnergy machine(BlockEntity host) {
        EnergyLimits limits = EnergyLimits.fe(CAPACITY, CAPACITY, CAPACITY)
                .withEuInput(EuTier.LV.voltage(), INPUT_AMPS)
                .withEuOutput(EuTier.MV.voltage(), OUTPUT_AMPS);
        EnergyPorts ports = new EnergyPorts(EnergyPorts.mask(Direction.NORTH), EnergyPorts.mask(Direction.SOUTH),
                EnergyPorts.mask(Direction.NORTH), EnergyPorts.mask(Direction.SOUTH));
        return new MachineEnergy(host, limits, ports);
    }

    @Nullable
    private static IEnergyContainer container(MachineEnergy energy, @Nullable Direction side) {
        return energy.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).orElse(null);
    }

    private static BlockEntity host(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        BlockEntity host = helper.getLevel().getBlockEntity(pos);
        if (host == null) {
            throw new IllegalStateException("no block entity to host the energy component at " + pos);
        }
        return host;
    }
}
