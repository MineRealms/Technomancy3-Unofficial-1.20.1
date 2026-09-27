package theflogat.technomancy.common.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class EssentiaPortsTest {

    @Test
    void jarPortsAreTopOnlyBothWays() {
        EssentiaPorts ports = EssentiaPorts.TOP;
        assertTrue(ports.canInputFrom(Direction.UP));
        assertTrue(ports.canOutputTo(Direction.UP));
        assertTrue(ports.isConnectable(Direction.UP));
        for (Direction face : Direction.values()) {
            if (face != Direction.UP) {
                assertFalse(ports.isConnectable(face), face + " must not connect");
            }
        }
    }

    @Test
    void directionIsNotSymmetric() {
        EssentiaPorts consumer = EssentiaPorts.consumer(Direction.NORTH, Direction.SOUTH);
        assertTrue(consumer.canInputFrom(Direction.NORTH));
        assertFalse(consumer.canOutputTo(Direction.NORTH));
        assertTrue(consumer.isConnectable(Direction.SOUTH), "an input-only face is still connectable");

        EssentiaPorts producer = EssentiaPorts.producer(Direction.DOWN);
        assertFalse(producer.canInputFrom(Direction.DOWN));
        assertTrue(producer.canOutputTo(Direction.DOWN));
        assertFalse(producer.isConnectable(Direction.UP));
    }

    @Test
    void aSidelessQueryNeverCarriesTransferRights() {
        for (EssentiaPorts ports : new EssentiaPorts[] {EssentiaPorts.ALL, EssentiaPorts.TOP, EssentiaPorts.NONE}) {
            assertFalse(ports.canInputFrom(null));
            assertFalse(ports.canOutputTo(null));
            assertFalse(ports.isConnectable(null));
        }
    }

    @Test
    void noPortsConnectNothing() {
        for (Direction face : Direction.values()) {
            assertFalse(EssentiaPorts.NONE.isConnectable(face));
            assertTrue(EssentiaPorts.ALL.isConnectable(face));
        }
    }

    @Test
    void masksOutsideTheSixFacesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EssentiaPorts(0b1000000, 0));
        assertThrows(IllegalArgumentException.class, () -> new EssentiaPorts(0, -1));
        assertEquals(0b111111, EssentiaPorts.mask(Direction.values()));
    }
}
