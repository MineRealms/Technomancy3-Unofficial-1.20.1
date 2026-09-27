package theflogat.technomancy.common.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.machines.CondenserBalance.Verdict;

/**
 * The perpetual-motion boundary condition, which is the reason these numbers are not free to
 * choose. The condenser makes potentia; the dynamo burns potentia; if the first costs no more
 * than the second returns, the pair is a generator.
 */
class CondenserBalanceTest {

    /** {@code balance.condenserCostQ} default in {@code TechnomancyConfig}. */
    private static final long DEFAULT_COST = 200_000;
    /** {@code balance.essentiaFuelScale} default in {@code TechnomancyConfig}. */
    private static final double DEFAULT_FUEL_SCALE = 0.25D;

    /**
     * Guards the shipped defaults themselves. The runtime check only logs, so this is the
     * assertion that actually stops a bad default from being released.
     */
    @Test
    void theShippedDefaultsAreNotAPerpetualMotionMachine() {
        assertEquals(16_000, CondenserBalance.dynamoYieldPerUnitQ(DEFAULT_FUEL_SCALE),
                "800 fuel x 80 Q x 0.25");
        assertEquals(Verdict.SAFE, CondenserBalance.verdict(DEFAULT_COST, DEFAULT_FUEL_SCALE));
        assertTrue(DEFAULT_COST > CondenserBalance.dynamoYieldPerUnitQ(DEFAULT_FUEL_SCALE) * 4,
                "the default keeps the recommended four-times margin");
    }

    /** The 1.7.10 numbers, which were safe by accident: 1,000,000 against 64,000. */
    @Test
    void theOriginalNumbersWereAlreadySafe() {
        assertEquals(64_000, CondenserBalance.dynamoYieldPerUnitQ(1.0D));
        assertEquals(Verdict.SAFE, CondenserBalance.verdict(1_000_000, 1.0D));
    }

    /** The boundary is strict: equal is already a closed loop that pays for itself. */
    @Test
    void equalCostAndYieldIsAlreadyPerpetualMotion() {
        long yield = CondenserBalance.dynamoYieldPerUnitQ(DEFAULT_FUEL_SCALE);
        assertEquals(Verdict.PERPETUAL_MOTION, CondenserBalance.verdict(yield, DEFAULT_FUEL_SCALE));
        assertEquals(Verdict.PERPETUAL_MOTION, CondenserBalance.verdict(yield - 1, DEFAULT_FUEL_SCALE));
        assertTrue(CondenserBalance.verdict(yield + 1, DEFAULT_FUEL_SCALE) != Verdict.PERPETUAL_MOTION,
                "one Q above the yield is a loss, however thin");
    }

    /** Legal but generous is a separate answer, so it can be warned about rather than refused. */
    @Test
    void theWarningBandSitsBetweenOneAndFourTimesTheYield() {
        long yield = CondenserBalance.dynamoYieldPerUnitQ(DEFAULT_FUEL_SCALE);
        assertEquals(Verdict.NARROW, CondenserBalance.verdict(yield + 1, DEFAULT_FUEL_SCALE));
        assertEquals(Verdict.NARROW, CondenserBalance.verdict(yield * 4, DEFAULT_FUEL_SCALE));
        assertEquals(Verdict.SAFE, CondenserBalance.verdict(yield * 4 + 1, DEFAULT_FUEL_SCALE));
    }

    /** Turning the fuel scale back to 1:1 without raising the cost is exactly the trap. */
    @Test
    void raisingTheFuelScaleCanPushASafeCostIntoTheWarningBand() {
        assertEquals(Verdict.SAFE, CondenserBalance.verdict(DEFAULT_COST, DEFAULT_FUEL_SCALE));
        assertEquals(Verdict.NARROW, CondenserBalance.verdict(DEFAULT_COST, 1.0D));
        assertEquals(Verdict.PERPETUAL_MOTION, CondenserBalance.verdict(DEFAULT_COST, 4.0D));
    }

    @Test
    void aZeroOrNegativeFuelScaleMeansEssentiaIsWorthlessAndAnyCostIsSafe() {
        assertEquals(0, CondenserBalance.dynamoYieldPerUnitQ(0.0D));
        assertEquals(0, CondenserBalance.dynamoYieldPerUnitQ(-1.0D));
        assertEquals(Verdict.SAFE, CondenserBalance.verdict(1, 0.0D));
    }

    /** An absurd scale must not wrap the margin multiplication into a "safe" answer. */
    @Test
    void anOverflowingYieldStaysUnsafe() {
        assertEquals(Long.MAX_VALUE, CondenserBalance.dynamoYieldPerUnitQ(Double.MAX_VALUE));
        assertEquals(Verdict.PERPETUAL_MOTION,
                CondenserBalance.verdict(Long.MAX_VALUE, Double.MAX_VALUE));

        // A yield above a quarter of the long range: four times it would wrap negative, so the
        // comfortable bound has to saturate rather than overflow into a SAFE verdict.
        double scale = 5.0E13D;
        long yield = CondenserBalance.dynamoYieldPerUnitQ(scale);
        assertTrue(yield > Long.MAX_VALUE / 4 && yield < Long.MAX_VALUE, "yield " + yield);
        assertEquals(Verdict.NARROW, CondenserBalance.verdict(Long.MAX_VALUE, scale));
    }

    @Test
    void everyVerdictExplainsItselfWithTheNumberThatMattered() {
        long yield = CondenserBalance.dynamoYieldPerUnitQ(DEFAULT_FUEL_SCALE);
        String broken = CondenserBalance.describe(yield, DEFAULT_FUEL_SCALE);
        assertTrue(broken.contains("free energy") && broken.contains(String.valueOf(yield)), broken);
        assertTrue(CondenserBalance.describe(yield + 1, DEFAULT_FUEL_SCALE).contains("legal"));
        assertTrue(CondenserBalance.describe(DEFAULT_COST, DEFAULT_FUEL_SCALE).contains("net energy sink"));
    }
}
