package theflogat.technomancy.gametest;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.machines.CondenserBalance;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;
import theflogat.technomancy.compat.gtceu.EuTier;

/**
 * The GTCEu half of {@link CondenserGameTests}, split off so the test holder itself carries no
 * {@code com.gregtechceu} type and can be loaded and reflected over on a runtime without
 * GTCEu. Nothing here is reachable unless {@code GtceuPresence.isLoaded()} was true.
 *
 * <p>This is the first block of the port with a real block entity, so it is also the first
 * time a GT sender can reach one of our machines by position rather than through a component
 * we hand it directly.</p>
 */
final class CondenserGtceuChecks {

    private CondenserGtceuChecks() {}

    static void euInput(GameTestHelper helper) {
        long rate = EnergyUnits.qPerEu();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = CondenserGameTests.place(helper, pos, Direction.NORTH);

        IEnergyContainer port = GTCapabilityHelper.getEnergyContainer(helper.getLevel(), pos, Direction.NORTH);
        helper.assertTrue(port != null,
                "the condenser exposes no EU container; a GT cable would have to fall back to FE");
        helper.assertTrue(port.inputsEnergy(Direction.NORTH) && !port.outputsEnergy(Direction.NORTH),
                "the condenser is a consumer and must never advertise EU output");
        helper.assertTrue(port.getInputVoltage() == EuTier.EV.voltage() && port.getInputAmperage() == 2,
                "the EU rating reads " + port.getInputVoltage() + "V " + port.getInputAmperage() + "A");
        helper.assertTrue(port.getOutputVoltage() == 0 && port.getOutputAmperage() == 0,
                "an input-only machine must not advertise an output rating");
        helper.assertTrue(port.getEnergyCapacity() == CondenserBalance.ENERGY_CAPACITY_Q / rate,
                "capacity reads " + port.getEnergyCapacity() + " EU for " + CondenserBalance.ENERGY_CAPACITY_Q
                        + " Q at " + rate + " Q/EU");

        // Over-voltage is refused outright rather than truncated, and costs no ampere quota.
        helper.assertTrue(port.acceptEnergyFromNetwork(Direction.NORTH, EuTier.IV.voltage(), 1) == 0,
                "a packet above the rated voltage was accepted");
        helper.assertTrue(condenser.energy().ledger().stored() == 0, "a refused packet still moved energy");

        long voltage = EuTier.MV.voltage();
        helper.assertTrue(port.acceptEnergyFromNetwork(Direction.NORTH, voltage, 5) == 2,
                "the rated ampere limit must cap the accepted packets");
        long delivered = 2 * voltage * rate;
        helper.assertTrue(condenser.energy().ledger().stored() == delivered,
                "the ledger holds " + condenser.energy().ledger().stored() + " Q after " + delivered + " Q of EU");

        // The same balance the FE view feeds is what the machine spends; there is only one.
        CondenserGameTests.tick(helper, pos, condenser, 1);
        helper.assertTrue(condenser.energy().ledger().stored() == 0 && condenser.unfinishedQ() == delivered,
                "EU that arrived natively was not converted: " + condenser.unfinishedQ() + " Q of progress from "
                        + delivered + " Q delivered");

        Technomancy.LOGGER.info("GameTest condenser EU: 2 A of {} EU ({} Q) arrived natively at {} Q per EU and"
                + " became conversion progress on the single ledger", voltage, delivered, rate);
        helper.succeed();
    }
}
