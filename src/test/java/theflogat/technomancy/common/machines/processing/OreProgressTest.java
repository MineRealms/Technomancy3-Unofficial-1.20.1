package theflogat.technomancy.common.machines.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The pass record: its limits, its normalisation and its NBT round trip. */
class OreProgressTest {

    private static final ProcessingModule TC = ProcessingModule.THAUMCRAFT;
    /** Stands in for the S3 Blood Magic module, to prove the limits are per module. */
    private static final ProcessingModule OTHER = new ProcessingModule("blood_magic", 2);

    @Test
    void eachModuleGetsTwoPassesAndTheStageIsCapped() {
        OreProgress first = OreProgress.NONE.next(TC);
        assertEquals(1, first.passes(TC));
        assertEquals(0, first.passes(OTHER));
        assertTrue(first.canProcess(TC, 0), "the second Thaumcraft pass is allowed");
        OreProgress second = first.next(TC);
        assertEquals(2, second.passes(TC));
        assertFalse(second.canProcess(TC, 1), "a third pass by the same module is not");
        assertTrue(second.canProcess(OTHER, 1), "another module may still take it");
        // Even with passes to spare, the last stage is the end of the chain: the original never
        // checked this and would have walked the damage value past the last smelting recipe.
        assertFalse(second.canProcess(OTHER, OreProgress.MAX_STAGE));
        assertThrows(IllegalStateException.class, () -> second.next(TC));
    }

    @Test
    void equalRecordsAreEqualSoIdenticalOresStack() {
        OreProgress a = OreProgress.NONE.next(TC).next(OTHER);
        OreProgress b = OreProgress.NONE.next(OTHER).next(TC);
        assertEquals(a, b, "insertion order must not matter");
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, OreProgress.NONE.next(TC));
        assertTrue(OreProgress.NONE.isEmpty());
        assertEquals(Map.of(TC.id(), 1, OTHER.id(), 1), a.allPasses());
    }

    @Test
    void nbtRoundTripsAndAnEmptyRecordWritesNothing() {
        CompoundTag tag = new CompoundTag();
        OreProgress.NONE.save(tag);
        assertTrue(tag.isEmpty(), "a clean ore must carry no tag at all, or it would not stack");
        assertEquals(OreProgress.NONE, OreProgress.load(tag));

        OreProgress progress = OreProgress.NONE.next(TC).next(TC).next(OTHER);
        progress.save(tag);
        OreProgress restored = OreProgress.load(tag);
        assertEquals(progress, restored);
        assertEquals(2, restored.passes(TC));
        assertEquals(1, restored.passes(OTHER));
    }

    @Test
    void forgedRecordsAreSanitised() {
        CompoundTag tag = new CompoundTag();
        CompoundTag passes = new CompoundTag();
        passes.putInt(TC.id(), 0);
        passes.putInt("negative", -4);
        passes.putInt("absurd", 1_000_000);
        passes.putString("wrong_type", "3");
        tag.put("Passes", passes);
        OreProgress loaded = OreProgress.load(tag);
        assertEquals(0, loaded.passes(TC), "a zero entry is no entry");
        assertEquals(Map.of("absurd", OreProgress.STAGES), loaded.allPasses());
    }

    @Test
    void theModuleIdIsAStableKey() {
        assertEquals("thaumcraft", TC.id());
        assertEquals(TC, ProcessingModule.byId("thaumcraft"));
        assertEquals(null, ProcessingModule.byId("botania"), "not in this build yet");
        assertThrows(IllegalArgumentException.class, () -> new ProcessingModule("Thaumcraft", 2));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingModule("tc", 0));
    }
}
