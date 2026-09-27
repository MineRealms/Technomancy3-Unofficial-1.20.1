package theflogat.technomancy.common.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The three-state redstone contract. {@code programmingItem} and {@code byProgrammingItem}
 * need the item registry and are covered by the GameTests instead.
 */
class RedstoneModeTest {

    @Test
    void theTruthTableMatchesTheOriginalSemantics() {
        assertTrue(RedstoneMode.NONE.canRun(true));
        assertTrue(RedstoneMode.NONE.canRun(false));
        assertTrue(RedstoneMode.HIGH.canRun(true));
        assertFalse(RedstoneMode.HIGH.canRun(false));
        assertFalse(RedstoneMode.LOW.canRun(true));
        assertTrue(RedstoneMode.LOW.canRun(false));
    }

    @Test
    void cyclingFollowsTheOriginalOrderAndReturnsHome() {
        assertEquals(RedstoneMode.HIGH, RedstoneMode.NONE.cycle());
        assertEquals(RedstoneMode.LOW, RedstoneMode.HIGH.cycle());
        assertEquals(RedstoneMode.NONE, RedstoneMode.LOW.cycle());
        for (RedstoneMode mode : RedstoneMode.values()) {
            assertEquals(mode, mode.cycle().cycle().cycle());
        }
    }

    @Test
    void everyModeRoundTripsThroughItsSavedId() {
        for (RedstoneMode mode : RedstoneMode.values()) {
            assertEquals(mode, RedstoneMode.byId(mode.id(), RedstoneMode.HIGH));
            assertEquals("technom.redstone_mode." + mode.id(), mode.translationKey());
        }
    }

    /**
     * An unreadable value falls back to the caller's default. The original hard-coded a
     * fallback of HIGH, which would have switched off any machine whose own default is LOW —
     * the condenser being exactly that case.
     */
    @Test
    void anUnknownIdFallsBackToTheMachinesOwnDefault() {
        // "Low" is the original's display-string form and is deliberately not accepted.
        assertEquals(RedstoneMode.NONE, RedstoneMode.byId("Low", RedstoneMode.NONE));
        assertEquals(RedstoneMode.LOW, RedstoneMode.byId("", RedstoneMode.LOW));
        assertEquals(RedstoneMode.LOW, RedstoneMode.byId("nonsense", RedstoneMode.LOW));
        assertEquals(RedstoneMode.NONE, RedstoneMode.byId("nonsense", RedstoneMode.NONE));
    }
}
