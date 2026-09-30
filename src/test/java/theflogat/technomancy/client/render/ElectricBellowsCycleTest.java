package theflogat.technomancy.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bellows' oscillator table has to be a real period, not a stretch of the recurrence.
 *
 * <p>The table exists so the bag can be drawn from {@code gameTime} alone, with no per-block
 * state. That only works if the sampled window wraps cleanly: whatever the table's length, the
 * step from its last entry back to its first has to look like every other step.</p>
 *
 * <p>It did not. {@code 0.075F} and {@code 0.025F} are not exact binary fractions, so the
 * recurrence drifts by about 4e-7 per pass and no two passes are bit-identical. The period search
 * demanded equality, found nothing, and fell back to handing out the whole 400-tick warm-up - so
 * the bag jumped once every twenty seconds, and it was a jump the original never had. The search
 * now compares within a tolerance; these tests pin the result.</p>
 */
class ElectricBellowsCycleTest {

    /** The recurrence's floor and ceiling, from {@code TileElectricBellows.updateEntity}. */
    private static final float TROUGH = 0.375F;
    private static final float CREST = 1.025F;
    /** The step the recurrence takes when it is climbing. */
    private static final float RISE = 0.025F;

    @Test
    void theTableIsExactlyOnePeriod() {
        assertEquals(ElectricBellowsRenderer.CYCLE_TICKS, ElectricBellowsRenderer.CYCLE.length,
                "the table must be one period long, or inflation() will alias");
    }

    @Test
    void theTableWrapsWithoutASeam() {
        float[] cycle = ElectricBellowsRenderer.CYCLE;
        float seam = Math.abs(cycle[0] - cycle[cycle.length - 1]);
        float largest = 0.0F;
        for (int i = 1; i < cycle.length; i++) {
            largest = Math.max(largest, Math.abs(cycle[i] - cycle[i - 1]));
        }
        assertTrue(seam <= largest,
                "the step from the last entry back to the first (" + seam + ") is bigger than any"
                        + " step inside the period (" + largest + "), so the bag would jump");
    }

    @Test
    void theTableSweepsFromTroughToCrestAndBack() {
        float[] cycle = ElectricBellowsRenderer.CYCLE;
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (float value : cycle) {
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        assertEquals(TROUGH, lowest, RISE / 2.0F, "the oscillator's floor moved");
        assertEquals(CREST, highest, RISE / 2.0F, "the oscillator's ceiling moved");
    }

    /**
     * Exactly two turning points per period, which is what makes it a sawtooth rather than a
     * wobble - and what tells {@code inflation} that a 35-tick window is the right one.
     */
    @Test
    void theTableTurnsAroundTwicePerPeriod() {
        float[] cycle = ElectricBellowsRenderer.CYCLE;
        int turns = 0;
        for (int i = 0; i < cycle.length; i++) {
            float before = cycle[i] - cycle[(i + cycle.length - 1) % cycle.length];
            float after = cycle[(i + 1) % cycle.length] - cycle[i];
            assertTrue(before != 0.0F && after != 0.0F,
                    "tick " + i + " does not move; the recurrence should always step");
            if (Math.signum(before) != Math.signum(after)) {
                turns++;
            }
        }
        assertEquals(2, turns, "the period turns around " + turns + " times, not twice");
    }
}
