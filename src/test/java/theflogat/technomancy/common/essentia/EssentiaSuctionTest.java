package theflogat.technomancy.common.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EssentiaSuctionTest {

    @Test
    void labelledStoresOutbidUnlabelledOnes() {
        EssentiaSuction jar = EssentiaSuction.JAR;
        assertEquals(64, jar.amount(true, false, false));
        assertEquals(32, jar.amount(false, false, false));
        assertTrue(EssentiaSuction.canTake(jar.amount(true, false, false), jar.amount(false, false, false)),
                "a labelled jar takes from an unlabelled one");
        assertFalse(EssentiaSuction.canTake(jar.amount(false, false, false), jar.amount(true, false, false)),
                "but not the other way round");
    }

    @Test
    void aFullStoreStopsPullingUnlessItVoids() {
        assertEquals(0, EssentiaSuction.JAR.amount(true, true, false));
        assertEquals(0, EssentiaSuction.JAR.amount(false, true, false));
        // A void jar keeps pulling in order to destroy what it takes, at its unlabelled figure.
        assertEquals(32, EssentiaSuction.VOID_JAR.amount(true, true, true));
        assertEquals(48, EssentiaSuction.VOID_JAR.amount(true, false, true));
        assertEquals(32, EssentiaSuction.VOID_JAR.amount(false, false, true));
    }

    @Test
    void aVoidJarOutbidsAnUnlabelledJarButNotALabelledOne() {
        int voidJar = EssentiaSuction.VOID_JAR.amount(true, false, true);
        int unlabelled = EssentiaSuction.JAR.amount(false, false, false);
        int labelled = EssentiaSuction.JAR.amount(true, false, false);
        assertTrue(EssentiaSuction.canTake(voidJar, unlabelled));
        assertFalse(EssentiaSuction.canTake(voidJar, labelled), "a labelled jar wins against the void jar");
    }

    @Test
    void equalSuctionNeverTransfers() {
        // Were the comparison not strict, two equal containers would trade one unit per tick forever.
        for (int suction : new int[] {0, 1, 32, 48, 64, Integer.MAX_VALUE}) {
            assertFalse(EssentiaSuction.canTake(suction, suction), "equal suction at " + suction);
            assertFalse(EssentiaSuction.canDiscover(suction, suction, 0), "equal suction at " + suction);
        }
        assertFalse(EssentiaSuction.canTake(0, 0), "a store with no suction takes nothing");
    }

    @Test
    void discoveryAlsoRequiresTheGiversMinimum() {
        // An unlabelled jar (32) facing a labelled jar's contents (minimum 64): the suction
        // comparison alone would let it adopt the aspect, the minimum stops it.
        assertTrue(EssentiaSuction.canTake(32, 0));
        assertFalse(EssentiaSuction.canDiscover(32, 0, 64));
        assertTrue(EssentiaSuction.canDiscover(64, 0, 64), "meeting the minimum exactly is enough");
        assertTrue(EssentiaSuction.canDiscover(65, 0, 64));
    }

    @Test
    void aBoundAspectSkipsTheMinimumJustAsTc4Does() {
        // TC4 (and TC4R's WardedJarBlockEntity.fillJar) re-check only the suction comparison
        // once the aspect is known, so a bound taker can outrank a giver's minimum.
        // Preserved on purpose: tubes in other mods depend on this shape.
        assertFalse(EssentiaSuction.canDiscover(33, 32, 64), "unbound: blocked by the minimum");
        assertTrue(EssentiaSuction.canTake(33, 32), "bound: the comparison alone decides");
    }

    @Test
    void minimumFollowsTheLabel() {
        assertEquals(64, EssentiaSuction.JAR.minimum(true));
        assertEquals(32, EssentiaSuction.JAR.minimum(false));
        assertEquals(48, EssentiaSuction.VOID_JAR.minimum(true));
        assertEquals(32, EssentiaSuction.VOID_JAR.minimum(false));
    }

    @Test
    void negativeSuctionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EssentiaSuction(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EssentiaSuction(0, -1));
    }
}
