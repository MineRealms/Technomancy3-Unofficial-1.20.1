package theflogat.technomancy.common.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * The affinity numbering both original versions got wrong: their constructor assigned the
 * parameter instead of the field, so every constant kept id 0 and lookups collapsed to EARTH.
 * This pins the ids and the mapping the rituals depend on.
 */
class AffinityTest {

    @Test
    void everyAffinityKeepsItsOwnId() {
        assertEquals(0, Affinity.EARTH.id());
        assertEquals(1, Affinity.FIRE.id());
        assertEquals(2, Affinity.WATER.id());
        assertEquals(3, Affinity.LIGHT.id());
        assertEquals(4, Affinity.DARK.id());
        assertEquals(5, Affinity.NORMAL.id());
        assertNotEquals(Affinity.EARTH.id(), Affinity.FIRE.id());
    }

    @Test
    void byIdRoundTrips() {
        for (Affinity affinity : Affinity.values()) {
            assertSame(affinity, Affinity.byId(affinity.id()));
        }
        assertSame(Affinity.NORMAL, Affinity.byId(99));
        assertSame(Affinity.NORMAL, Affinity.byId(-1));
    }

    @Test
    void ritualTypesMapOntoTheSharedNumbering() {
        assertEquals(Affinity.EARTH, Ritual.Type.EARTH.affinity());
        assertEquals(Affinity.FIRE, Ritual.Type.FIRE.affinity());
        assertEquals(Affinity.WATER, Ritual.Type.WATER.affinity());
        assertEquals(Affinity.LIGHT, Ritual.Type.LIGHT.affinity());
        assertEquals(Affinity.DARK, Ritual.Type.DARK.affinity());
        assertEquals(Ritual.Type.EARTH.id(), Affinity.EARTH.id());
        assertEquals(Ritual.Type.DARK.id(), Affinity.DARK.id());
    }

    @Test
    void persistentKeysAreStableAndDistinct() {
        assertEquals("technom:affinity_earth", Affinity.EARTH.key());
        assertEquals("technom:affinity_dark", Affinity.DARK.key());
        assertNotEquals(Affinity.EARTH.key(), Affinity.NORMAL.key());
    }
}
