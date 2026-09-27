package theflogat.technomancy.common.machines.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.machines.processing.ProcessorCycle.Outcome;

/**
 * The processor's accounting. The headline assertion is {@link #aFullOutputCostsNothing()}: the
 * 1.7.10 base class paid fuel for a whole 60-tick cycle before it ever looked at the output slot.
 */
class ProcessorCycleTest {

    /** Counts what was spent, and can be told to refuse. */
    private static final class Purse implements ProcessorCycle.Payer {
        int balance;
        int spent;

        Purse(int balance) {
            this.balance = balance;
        }

        @Override
        public boolean pay(int cost) {
            if (balance < cost) {
                return false;
            }
            balance -= cost;
            spent += cost;
            return true;
        }
    }

    private static final Object JOB = "iron";
    private static final Object OTHER_JOB = "gold";

    @Test
    void aJobTakesSixtyPaidTicks() {
        ProcessorCycle cycle = new ProcessorCycle();
        Purse purse = new Purse(1000);
        for (int tick = 1; tick < ProcessorCycle.TICKS; tick++) {
            assertEquals(Outcome.WORKED, cycle.tick(JOB, true, 2, purse), "tick " + tick);
            assertEquals(tick, cycle.progress());
        }
        assertEquals(Outcome.COMPLETED, cycle.tick(JOB, true, 2, purse));
        assertEquals(0, cycle.progress(), "the counter is ready for the next job");
        assertEquals(2 * ProcessorCycle.TICKS, purse.spent, "a job costs exactly 60 ticks of fuel");
    }

    @Test
    void aFullOutputCostsNothing() {
        ProcessorCycle cycle = new ProcessorCycle();
        Purse purse = new Purse(1000);
        for (int tick = 0; tick < 200; tick++) {
            assertEquals(Outcome.BLOCKED, cycle.tick(JOB, false, 2, purse));
        }
        assertEquals(0, purse.spent, "a blocked processor must not spend anything");
        assertEquals(0, cycle.progress());
        // And it picks up again the moment the output is emptied.
        assertEquals(Outcome.WORKED, cycle.tick(JOB, true, 2, purse));
        assertEquals(2, purse.spent);
    }

    @Test
    void progressSurvivesStarvationAndBlockingWithoutAdvancing() {
        ProcessorCycle cycle = new ProcessorCycle();
        Purse purse = new Purse(6);
        assertEquals(Outcome.WORKED, cycle.tick(JOB, true, 2, purse));
        assertEquals(Outcome.WORKED, cycle.tick(JOB, true, 2, purse));
        assertEquals(Outcome.WORKED, cycle.tick(JOB, true, 2, purse));
        assertEquals(3, cycle.progress());
        assertEquals(Outcome.STARVED, cycle.tick(JOB, true, 2, purse), "the purse is empty");
        assertEquals(3, cycle.progress(), "a tick that was not paid for does not count");
        assertEquals(Outcome.BLOCKED, cycle.tick(JOB, false, 2, purse));
        assertEquals(3, cycle.progress());
        assertEquals(Outcome.IDLE, cycle.tick(null, true, 2, purse), "an empty slot is idle");
        assertEquals(6, purse.spent);
    }

    @Test
    void changingTheJobRestartsIt() {
        ProcessorCycle cycle = new ProcessorCycle();
        Purse purse = new Purse(1000);
        cycle.tick(JOB, true, 2, purse);
        cycle.tick(JOB, true, 2, purse);
        assertEquals(2, cycle.progress());
        cycle.tick(OTHER_JOB, true, 2, purse);
        assertEquals(1, cycle.progress(), "a different job starts from zero, plus this tick");
    }

    @Test
    void savedProgressIsAdoptedByTheSameJobAndDroppedWithAnEmptySlot() {
        ProcessorCycle cycle = new ProcessorCycle();
        Purse purse = new Purse(1000);
        for (int tick = 0; tick < 30; tick++) {
            cycle.tick(JOB, true, 2, purse);
        }
        CompoundTag saved = cycle.save();

        ProcessorCycle restored = new ProcessorCycle();
        restored.load(saved);
        assertEquals(30, restored.progress());
        restored.adopt(JOB);
        assertEquals(Outcome.WORKED, restored.tick(JOB, true, 2, purse));
        assertEquals(31, restored.progress(), "a reload must not throw away a nearly finished job");

        ProcessorCycle emptied = new ProcessorCycle();
        emptied.load(saved);
        emptied.adopt(null);
        assertEquals(0, emptied.progress(), "nothing in the slot, nothing to resume");
    }

    @Test
    void loadClampsAndRejectsNonPositiveCosts() {
        ProcessorCycle cycle = new ProcessorCycle();
        CompoundTag tag = new CompoundTag();
        tag.putInt("progress", 5000);
        cycle.load(tag);
        assertEquals(ProcessorCycle.TICKS - 1, cycle.progress(), "clamped below one whole job");
        tag.putInt("progress", -20);
        cycle.load(tag);
        assertEquals(0, cycle.progress());
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessorCycle().tick(JOB, true, 0, new Purse(10)));
    }

    @Test
    void theLegacyCostFormulaIsReproduced() {
        // max(1, resultStage + 2 * resultPasses): 2 for a raw ore, 5 for the second pass.
        assertEquals(2, OreProcessing.tickCost(0, 1));
        assertEquals(5, OreProcessing.tickCost(1, 2));
        assertEquals(4, OreProcessing.tickCost(2, 1));
        assertEquals(1, OreProcessing.tickCost(0, 0), "never free, never negative");
        assertEquals(1, OreProcessing.tickCost(-5, 0));
        assertSame(Outcome.IDLE, new ProcessorCycle().tick(null, true, 1, new Purse(0)));
    }
}
