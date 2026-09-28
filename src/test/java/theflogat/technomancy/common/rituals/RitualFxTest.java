package theflogat.technomancy.common.rituals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The parts of {@link RitualFx} that need no world. The effects themselves need a
 * {@code ServerLevel} to send from, so what is left to check here is the colour mapping every
 * shape is drawn in.
 */
class RitualFxTest {

    @Test
    void everyRitualTypeHasItsOwnColour() {
        Set<Integer> seen = new HashSet<>();
        for (Ritual.Type type : Ritual.Type.values()) {
            int rgb = RitualFx.colour(type);
            assertTrue(seen.add(rgb), type + " shares a colour with an earlier type");
            // Anything outside 0x000000..0xFFFFFF would be dropped by the vertex colour.
            assertEquals(rgb, rgb & 0xFFFFFF, type + " colour is out of range");
        }
        assertEquals(5, seen.size(), "there are five ritual types and should be five colours");
    }

    @Test
    void aMissingAspectFallsBackToWhiteRatherThanBlack() {
        // A bolt drawn in black would be invisible against the night, so the fallback is white.
        assertEquals(0xFFFFFF, RitualFx.aspectColour(null));
    }

    @Test
    void theElementColoursAreTheOnesTheTomeAndHudAlreadyUse() {
        // Pinned so a re-tint cannot silently make the effect disagree with everything else.
        assertEquals(0x3CB371, RitualFx.colour(Ritual.Type.EARTH));
        assertEquals(0xFF6A00, RitualFx.colour(Ritual.Type.FIRE));
        assertEquals(0x3A7BFF, RitualFx.colour(Ritual.Type.WATER));
        assertEquals(0xFFF7B0, RitualFx.colour(Ritual.Type.LIGHT));
        assertEquals(0x8A2BE2, RitualFx.colour(Ritual.Type.DARK));
    }
}
