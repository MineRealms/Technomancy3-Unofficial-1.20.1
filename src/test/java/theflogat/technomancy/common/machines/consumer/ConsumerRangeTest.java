package theflogat.technomancy.common.machines.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How many layers each eldritch consumer range actually scans.
 *
 * <p>{@code TileEldritchConsumer.seekForBlock} ran {@code yy > y - h - 1}, i.e. {@code h} layers
 * starting at {@code y - 1}, while its mob and item box started one layer lower still. The port
 * used the box floor for both, so the block scan ate one layer more than the range advertised -
 * TINY took two layers instead of one.</p>
 */
class ConsumerRangeTest {

    private static final int FLOOR = -64;
    private static final int ORIGIN = 70;

    @Test
    void aFiniteHeightScansExactlyThatManyLayers() {
        for (ConsumerRange range : ConsumerRange.values()) {
            int height = range.height();
            if (height < 0) {
                continue;
            }
            int lowest = range.blockFloorY(ORIGIN, FLOOR);
            int layers = ORIGIN - 1 - lowest + 1;
            assertEquals(height, layers, range + " scans " + layers + " layers, not " + height);
        }
    }

    @Test
    void theEntityBoxReachesOneLayerLowerThanTheBlockScan() {
        for (ConsumerRange range : ConsumerRange.values()) {
            int height = range.height();
            if (height < 0) {
                continue;
            }
            assertEquals(range.blockFloorY(ORIGIN, FLOOR) - 1, range.entityFloorY(ORIGIN, FLOOR),
                    range + ": the entity box must start one layer below the block scan");
        }
    }

    @Test
    void anUnboundedHeightFallsToTheBottomOfTheWorld() {
        for (ConsumerRange range : ConsumerRange.values()) {
            if (range.height() >= 0) {
                continue;
            }
            assertEquals(FLOOR, range.blockFloorY(ORIGIN, FLOOR), range + ": block scan");
            assertEquals(FLOOR, range.entityFloorY(ORIGIN, FLOOR), range + ": entity box");
        }
    }

    @Test
    void theWorldFloorClampsAMachineNearTheBottom() {
        assertEquals(FLOOR, ConsumerRange.AVERAGE.blockFloorY(FLOOR + 2, FLOOR));
        assertEquals(FLOOR, ConsumerRange.AVERAGE.entityFloorY(FLOOR + 2, FLOOR));
    }
}
