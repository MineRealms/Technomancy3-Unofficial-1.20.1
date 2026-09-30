package theflogat.technomancy.compat.gtceu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.energy.EnergyUnits;

class EuRatingTest {

    /**
     * The nine machines that derive an EU input rating, as {@code {peak draw Q/t, input budget Q/t}}.
     * Each pair names the constant it comes from, so a change to one of those constants shows up
     * here as a diff rather than as a silently stale expectation.
     */
    private static final long[][] MACHINES = {
        // Mana Exchanger: EXCHANGER_COST, ENERGY_CAPACITY.
        {1_000, 10_000},
        // Mana Fabricator: balance.manaFabricatorCostQ default, ENERGY_CAPACITY.
        {1_000_000, 2_000_000},
        // Node Fabricator: NodeFabricatorWork.EXPAND_ENERGY, ENERGY_CAPACITY.
        {10_000, 50_000_000},
        // Existence Burner (dynamic): DYNAMIC_ENERGY_CAPACITY, DYNAMIC_ENERGY_CAPACITY.
        {100_000, 100_000},
        // Essentia Fusor: EssentiaFusorBalance.ENERGY_CAPACITY_Q, which is the worst-case price.
        {2_000_000, 2_000_000},
        // Biome Morpher: balance.biomeMorpherCostQ default, ENERGY_CAPACITY.
        {20_000, 800_000},
        // Electric Bellows: STOKE_COST, ENERGY_CAPACITY.
        {3_000, 20_000},
        // Eldritch Consumer: ENERGY_CAPACITY, because the whole buffer can go in one tick.
        {1_000_000, 1_000_000},
        // Energy Condenser: CondenserBalance.MAX_RATE_Q_PER_TICK, ENERGY_CAPACITY_Q.
        {8_192, 100_000},
    };

    /** The tiers those nine pairs resolve to at the shipped four Q per EU. */
    private static final EuTier[] EXPECTED = {
        EuTier.HV, EuTier.ZPM, EuTier.IV, EuTier.IV, EuTier.ZPM,
        EuTier.IV, EuTier.EV, EuTier.ZPM, EuTier.EV,
    };

    private static long qPerEu() {
        return EnergyUnits.qPerEu();
    }

    @Test
    void everyMachineResolvesToItsDocumentedTierAtTheShippedRate() {
        Assumptions.assumeTrue(qPerEu() == EnergyUnits.DEFAULT_Q_PER_EU,
                "the table below is written for the shipped " + EnergyUnits.DEFAULT_Q_PER_EU + " Q per EU");
        for (int i = 0; i < MACHINES.length; i++) {
            long voltage = EuRating.inputVoltage(MACHINES[i][0], MACHINES[i][1]);
            assertEquals(EXPECTED[i].voltage(), voltage,
                    "machine " + i + " is rated " + voltage + " V, expected " + EXPECTED[i]);
        }
    }

    @Test
    void aPacketAlwaysFitsTheInputBudget() {
        for (long[] machine : MACHINES) {
            long voltage = EuRating.inputVoltage(machine[0], machine[1]);
            assertTrue(voltage > 0, "no rating for peak " + machine[0] + " with budget " + machine[1]);
            assertTrue(voltage * qPerEu() <= machine[1],
                    "a " + voltage + " V packet is " + voltage * qPerEu() + " Q, larger than the "
                            + machine[1] + " Q budget, so the machine could never accept one");
        }
    }

    /**
     * The rating is the lowest tier that both funds the dearest tick and fits the budget. Where no
     * tier can do both it is instead the highest tier that fits, and the machine needs more than
     * one ampere to reach full rate — which is the case for the fabricator and the fusor.
     */
    @Test
    void noLowerTierWouldAlsoHaveWorked() {
        for (long[] machine : MACHINES) {
            long voltage = EuRating.inputVoltage(machine[0], machine[1]);
            EuTier tier = EuTier.forVoltage(voltage);
            if (tier.index() == 0) {
                continue;
            }
            EuTier lower = EuTier.byIndex(tier.index() - 1);
            boolean fundsDraw = lower.voltage() * qPerEu() >= machine[0];
            boolean fitsBudget = lower.voltage() * qPerEu() <= machine[1];
            assertTrue(!(fundsDraw && fitsBudget), lower + " would also have worked, so " + tier
                    + " is rated higher than it needs to be");
        }
    }

    @Test
    void roundsTheDrawUpToTheNextTierRatherThanDown() {
        // Exactly one EV ampere's worth of draw is EV; one Q more is IV.
        assertEquals(EuTier.EV.voltage(),
                EuRating.inputVoltage(EuTier.EV.voltage() * qPerEu(), Long.MAX_VALUE));
        assertEquals(EuTier.IV.voltage(),
                EuRating.inputVoltage(EuTier.EV.voltage() * qPerEu() + 1, Long.MAX_VALUE));
    }

    @Test
    void refusesToRateAMachineNoPacketWouldFit() {
        assertEquals(0, EuRating.inputVoltage(0, 100_000), "no draw means no rating");
        assertEquals(0, EuRating.inputVoltage(1_000, 0), "no budget means no rating");
        // One Q below a ULV packet: every tier is too big, so the machine keeps its FE-only rating.
        assertEquals(0, EuRating.inputVoltage(1_000, EuTier.ULV.voltage() * qPerEu() - 1));
    }
}
