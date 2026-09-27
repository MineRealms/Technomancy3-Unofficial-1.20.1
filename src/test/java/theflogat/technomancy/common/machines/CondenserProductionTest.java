package theflogat.technomancy.common.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.energy.EnergyLedger;
import theflogat.technomancy.common.energy.EnergyLimits;

class CondenserProductionTest {

    private static final long COST = 200_000;
    private static final long RATE = 8_192;
    private static final int ROOM = 64;

    private static EnergyLedger ledger(long stored) {
        EnergyLedger ledger = new EnergyLedger(
                EnergyLimits.fe(CondenserBalance.ENERGY_CAPACITY_Q, CondenserBalance.ENERGY_CAPACITY_Q, 0));
        ledger.generate(stored);
        return ledger;
    }

    private static CondenserProduction production() {
        return new CondenserProduction(COST, RATE);
    }

    /**
     * The central property: over a whole cycle the energy that leaves the ledger equals the
     * cost of the unit produced, to the Q, with nothing left stranded in the progress counter.
     * Every single tick is checked against the same books, so a leak cannot hide inside one.
     */
    @Test
    void aFullCycleSpendsExactlyTheCostAndAccountsForEveryTick() {
        EnergyLedger ledger = ledger(0);
        CondenserProduction production = production();
        long supplied = 0;
        int units = 0;
        int ticks = 0;
        while (units == 0 && ticks < 1000) {
            ticks++;
            supplied += ledger.generate(RATE);
            long energyBefore = ledger.stored();
            long progressBefore = production.progressQ();

            int produced = production.tick(ledger, ROOM);

            long spent = energyBefore - ledger.stored();
            units += produced;
            assertTrue(spent >= 0 && spent <= RATE, "tick " + ticks + " spent " + spent);
            // Energy is neither created nor destroyed: what left the ledger either sits in the
            // progress counter or was turned into a unit at exactly the rated cost.
            assertEquals(spent, production.progressQ() - progressBefore + produced * COST,
                    "tick " + ticks + " does not balance");
        }
        assertEquals(1, units, "one unit after " + ticks + " ticks");
        assertEquals(25, ticks, "24 full-rate ticks plus a short last one");
        assertEquals(COST, supplied - ledger.stored(), "the cycle consumed something other than the cost");
        assertEquals(0, production.progressQ(), "a completed unit must leave no remainder");
    }

    /** The last tick of a cycle takes only the remainder, so the cost is never overshot. */
    @Test
    void theRateCapsEveryTickButTheLastOneTakesOnlyWhatIsLeft() {
        EnergyLedger ledger = ledger(CondenserBalance.ENERGY_CAPACITY_Q);
        CondenserProduction production = production();
        long full = COST / RATE;
        for (long tick = 0; tick < full; tick++) {
            ledger.generate(RATE);
            long before = ledger.stored();
            assertEquals(0, production.tick(ledger, ROOM));
            assertEquals(RATE, before - ledger.stored(), "tick " + tick + " was not rate limited");
        }
        assertEquals(full * RATE, production.progressQ());
        ledger.generate(RATE);
        long before = ledger.stored();
        assertEquals(1, production.tick(ledger, ROOM));
        assertEquals(COST - full * RATE, before - ledger.stored(), "the last tick overshot the cost");
    }

    /** A drained ledger changes nothing at all: the spend is all-or-nothing, never partial. */
    @Test
    void anEmptyLedgerLeavesEverythingUntouched() {
        EnergyLedger ledger = ledger(0);
        CondenserProduction production = production();
        assertEquals(0, production.tick(ledger, ROOM));
        assertEquals(0, ledger.stored());
        assertEquals(0, production.progressQ());
    }

    /** Less than a tick's worth available: take all of it, credit all of it, lose none of it. */
    @Test
    void aSupplySmallerThanTheRateIsSpentInFull() {
        EnergyLedger ledger = ledger(100);
        CondenserProduction production = production();
        assertEquals(0, production.tick(ledger, ROOM));
        assertEquals(0, ledger.stored());
        assertEquals(100, production.progressQ());
    }

    /**
     * A full essentia buffer must not burn energy it cannot turn into anything. The 1.7.10
     * dynamo had the mirror image of this and consumed fuel for nothing (defect A-14).
     */
    @Test
    void noRoomMeansNoSpending() {
        EnergyLedger ledger = ledger(CondenserBalance.ENERGY_CAPACITY_Q);
        CondenserProduction production = production();
        assertEquals(0, production.tick(ledger, 0));
        assertEquals(CondenserBalance.ENERGY_CAPACITY_Q, ledger.stored());
        assertEquals(0, production.progressQ());
    }

    /** Stopping mid-cycle parks the energy already spent instead of throwing it away. */
    @Test
    void partialProgressSurvivesASaveAndCostsNothingExtra() {
        EnergyLedger ledger = ledger(CondenserBalance.ENERGY_CAPACITY_Q);
        CondenserProduction first = production();
        long supplied = CondenserBalance.ENERGY_CAPACITY_Q;
        for (int tick = 0; tick < 5; tick++) {
            supplied += ledger.generate(RATE);
            first.tick(ledger, ROOM);
        }
        assertEquals(5 * RATE, first.progressQ());

        CompoundTag saved = first.save();
        CondenserProduction restored = production();
        assertFalse(restored.load(saved), "a round trip must be lossless");
        assertEquals(5 * RATE, restored.progressQ());

        int units = 0;
        for (int tick = 0; tick < 1000 && units == 0; tick++) {
            supplied += ledger.generate(RATE);
            units += restored.tick(ledger, ROOM);
        }
        assertEquals(1, units);
        assertEquals(COST, supplied - ledger.stored(), "the interrupted cycle cost more or less than one unit");
    }

    @Test
    void savedProgressIsClampedIntoTheCurrentCost() {
        CondenserProduction production = production();
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 1);
        tag.putLong("q", COST * 10);
        assertTrue(production.load(tag), "an out-of-range value must be reported");
        assertEquals(COST - 1, production.progressQ(), "progress must stay below the cost");

        tag.putLong("q", -5);
        assertTrue(production.load(tag));
        assertEquals(0, production.progressQ());
    }

    @Test
    void anUnknownSchemaIsDiscardedRatherThanGuessedAt() {
        CondenserProduction production = production();
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 99);
        tag.putLong("q", 1234);
        assertTrue(production.load(tag));
        assertEquals(0, production.progressQ());
        assertFalse(production.load(new CompoundTag()), "an absent tag is a fresh machine, not a loss");
    }

    @Test
    void degenerateSettingsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new CondenserProduction(0, RATE));
        assertThrows(IllegalArgumentException.class, () -> new CondenserProduction(COST, 0));
        assertThrows(IllegalArgumentException.class, () -> new CondenserProduction(-1, -1));
    }

    @Test
    void progressFractionTracksTheCycle() {
        EnergyLedger ledger = ledger(CondenserBalance.ENERGY_CAPACITY_Q);
        CondenserProduction production = production();
        assertEquals(0.0F, production.progressFraction());
        production.tick(ledger, ROOM);
        assertEquals((float) RATE / COST, production.progressFraction());
    }
}
