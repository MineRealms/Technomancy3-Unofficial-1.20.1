package theflogat.technomancy.common.tiles.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * An infinite source still has to honour TC4R's bounds: {@code EssentiaApi.findSource/extract}
 * throws on anything but the full request or zero, and {@code EssentiaApi.take} throws on a
 * result outside {@code [0, requested]}.
 */
class CreativeJarContractTest {

    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId AQUA = AspectId.parse("aqua");

    @Test
    void sourceExtractionIsAllOrNothingForEveryRequestSize() {
        for (int amount : new int[] {1, 7, 8, 64, 320, 321, 100_000, Integer.MAX_VALUE}) {
            int result = CreativeJarContract.extract(IGNIS, IGNIS, amount);
            assertEquals(amount, result, "an infinite source satisfies any positive request in full");
        }
    }

    @Test
    void wrongUnsetOrNonPositiveRequestsYieldZero() {
        assertEquals(0, CreativeJarContract.extract(IGNIS, AQUA, 8), "wrong aspect");
        assertEquals(0, CreativeJarContract.extract(null, IGNIS, 8), "unset jar");
        assertEquals(0, CreativeJarContract.extract(IGNIS, null, 8), "no aspect requested");
        assertEquals(0, CreativeJarContract.extract(IGNIS, IGNIS, 0));
        assertEquals(0, CreativeJarContract.extract(IGNIS, IGNIS, -5));
        assertEquals(0, CreativeJarContract.extract(IGNIS, IGNIS, Integer.MIN_VALUE));
    }

    @Test
    void transportTakeStaysWithinTheRequestedBoundsOnEveryFace() {
        for (Direction face : Direction.values()) {
            for (int amount : new int[] {1, 5, 64, 1000}) {
                int taken = CreativeJarContract.take(IGNIS, IGNIS, amount, face);
                assertTrue(taken >= 0 && taken <= amount, face + " returned " + taken + " for " + amount);
                assertEquals(face == Direction.UP ? amount : 0, taken, face + ": only the top face gives");
            }
        }
        assertEquals(0, CreativeJarContract.take(IGNIS, IGNIS, 5, null), "a side-less query carries no rights");
    }

    @Test
    void availabilityIsPositiveExactlyWhereTakingSucceeds() {
        // EssentiaApi.take refuses without calling takeEssentia when availableEssentia <= 0, so
        // the two must agree or the jar would be silently undrainable.
        for (Direction face : Direction.values()) {
            boolean available = CreativeJarContract.available(IGNIS, IGNIS, face) > 0;
            boolean takes = CreativeJarContract.take(IGNIS, IGNIS, 1, face) > 0;
            assertEquals(takes, available, face.toString());
        }
        assertEquals(0, CreativeJarContract.available(IGNIS, AQUA, Direction.UP));
        assertEquals(0, CreativeJarContract.available(null, IGNIS, Direction.UP));
        assertFalse(CreativeJarContract.isOutputFace(Direction.DOWN));
        assertEquals(0, CreativeJarContract.MINIMUM_SUCTION, "any taker may drain a test source");
    }
}
