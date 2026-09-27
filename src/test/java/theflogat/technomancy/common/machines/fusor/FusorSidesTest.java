package theflogat.technomancy.common.machines.fusor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.machines.fusor.FusorSides.SideType;

/**
 * The fusor's slots and, above all, its conservation: a fusion is 2 units in for 1 out, and no
 * other method in the class moves essentia between slots.
 */
class FusorSidesTest {

    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId ORDO = AspectId.parse("ordo");
    private static final AspectId AQUA = AspectId.parse("aqua");
    private static final AspectId POTENTIA = AspectId.parse("potentia");

    /** Stands in for the live registry: ignis + ordo make potentia, nothing else combines. */
    private static final FusorSides.Combiner COMBINER = (first, second) -> {
        boolean pair = IGNIS.equals(first) && ORDO.equals(second) || ORDO.equals(first) && IGNIS.equals(second);
        return pair ? POTENTIA : null;
    };

    private static FusorSides marked() {
        FusorSides sides = new FusorSides();
        assertTrue(sides.markInput(Direction.NORTH, IGNIS, COMBINER));
        assertTrue(sides.markInput(Direction.SOUTH, ORDO, COMBINER));
        assertTrue(sides.markOutput(Direction.WEST, COMBINER));
        return sides;
    }

    @Test
    void aRecipeNeedsTwoInputsThatCombineAndAnOutput() {
        FusorSides sides = new FusorSides();
        assertFalse(sides.fullyMarked());
        assertTrue(sides.markInput(Direction.NORTH, IGNIS, COMBINER));
        assertFalse(sides.markInput(Direction.SOUTH, AQUA, COMBINER),
                "a second input that does not combine must be refused");
        assertEquals(SideType.NONE, sides.type(Direction.SOUTH));
        assertTrue(sides.markInput(Direction.SOUTH, ORDO, COMBINER));
        assertFalse(sides.markInput(Direction.EAST, IGNIS, COMBINER), "only two inputs");
        assertNull(sides.outputAspect(), "no output slot yet, so no recipe yet");
        assertTrue(sides.markOutput(Direction.WEST, COMBINER));
        assertEquals(POTENTIA, sides.outputAspect(), "marking the output last resolves the recipe");
        assertFalse(sides.markOutput(Direction.EAST, COMBINER), "only one output");
        assertTrue(sides.fullyMarked());
        assertFalse(FusorSides.isSlot(Direction.UP), "up and down are never slots");
        assertFalse(sides.markInput(Direction.UP, IGNIS, COMBINER));
    }

    @Test
    void markingTheOutputFirstAlsoResolvesTheRecipe() {
        FusorSides sides = new FusorSides();
        assertTrue(sides.markOutput(Direction.WEST, COMBINER));
        assertNull(sides.outputAspect());
        assertTrue(sides.markInput(Direction.NORTH, IGNIS, COMBINER));
        assertNull(sides.outputAspect(), "one input is not a recipe");
        assertTrue(sides.markInput(Direction.SOUTH, ORDO, COMBINER));
        assertEquals(POTENTIA, sides.outputAspect());
    }

    @Test
    void aFusionIsTwoUnitsInForOneOut() {
        FusorSides sides = marked();
        assertEquals(10, sides.add(Direction.NORTH, IGNIS, 10, false));
        assertEquals(10, sides.add(Direction.SOUTH, ORDO, 10, false));
        assertEquals(20, sides.totalStored());
        for (int fusion = 1; fusion <= 10; fusion++) {
            assertTrue(sides.canFuse(), "fusion " + fusion);
            sides.fuse();
            assertEquals(10 - fusion, sides.amount(Direction.NORTH));
            assertEquals(10 - fusion, sides.amount(Direction.SOUTH));
            assertEquals(fusion, sides.amount(Direction.WEST));
            assertEquals(20 - fusion, sides.totalStored(), "each fusion consumes exactly two for one");
        }
        assertFalse(sides.canFuse(), "the inputs are empty");
        assertThrows(IllegalStateException.class, sides::fuse);
    }

    @Test
    void aFullOutputAndAMissingInputBothStopTheFusion() {
        FusorSides sides = marked();
        sides.add(Direction.NORTH, IGNIS, 64, false);
        sides.add(Direction.SOUTH, ORDO, 64, false);
        // Fill the output to the brim by fusing; 64 in each input makes 64 output.
        for (int fusion = 0; fusion < FusorSides.MAX_AMOUNT; fusion++) {
            sides.fuse();
        }
        assertEquals(FusorSides.MAX_AMOUNT, sides.amount(Direction.WEST));
        assertFalse(sides.canFuse(), "a full output must stop the machine");
        assertEquals(0, sides.add(Direction.WEST, POTENTIA, 1, false), "the output is not an input");

        FusorSides half = marked();
        half.add(Direction.NORTH, IGNIS, 5, false);
        assertFalse(half.canFuse(), "one empty input is enough to stop it");
    }

    @Test
    void slotsOnlyAcceptTheirOwnAspectAndSimulationChangesNothing() {
        FusorSides sides = marked();
        assertEquals(0, sides.add(Direction.NORTH, ORDO, 5, false), "wrong aspect");
        assertEquals(0, sides.add(Direction.EAST, IGNIS, 5, false), "unmarked slot");
        assertEquals(5, sides.add(Direction.NORTH, IGNIS, 5, true), "simulated insert");
        assertEquals(0, sides.totalStored(), "a simulated insert stored something");
        sides.add(Direction.NORTH, IGNIS, 5, false);
        assertEquals(3, sides.take(Direction.NORTH, IGNIS, 3, true));
        assertEquals(5, sides.amount(Direction.NORTH), "a simulated take removed something");
        assertEquals(5, sides.take(Direction.NORTH, IGNIS, 50, false), "a take is bounded by the contents");
        assertEquals(0, sides.take(Direction.NORTH, AQUA, 1, false), "wrong aspect");
        assertEquals(FusorSides.MAX_AMOUNT,
                sides.add(Direction.NORTH, IGNIS, 1000, false), "bounded by the capacity");
    }

    @Test
    void unmarkingIsRefusedWhileAnythingIsStored() {
        FusorSides sides = marked();
        sides.add(Direction.NORTH, IGNIS, 1, false);
        assertNull(sides.clear(Direction.NORTH), "a slot holding essentia must not be unmarked");
        sides.add(Direction.SOUTH, ORDO, 1, false);
        sides.fuse();
        assertEquals(1, sides.amount(Direction.WEST));
        assertNull(sides.clear(Direction.SOUTH), "the output holds the result, so nothing may change");
        assertEquals(1, sides.take(Direction.WEST, POTENTIA, 1, false));

        FusorSides.Cleared cleared = sides.clear(Direction.NORTH);
        assertNotNull(cleared);
        assertEquals(SideType.INPUT, cleared.type());
        assertEquals(IGNIS, cleared.aspect(), "the phial handed back has to hold the right aspect");
        assertNull(sides.outputAspect(), "the recipe is incomplete, so the output forgets it");
        assertEquals(SideType.OUTPUT, sides.type(Direction.WEST), "the output keeps its slot");
        assertFalse(sides.fullyMarked());
        assertNull(sides.clear(Direction.EAST), "an unmarked slot has nothing to give back");
    }

    @Test
    void nbtRoundTripsIncludingTheAmounts() {
        FusorSides sides = marked();
        sides.add(Direction.NORTH, IGNIS, 7, false);
        sides.add(Direction.SOUTH, ORDO, 9, false);
        sides.fuse();
        CompoundTag tag = sides.save();

        FusorSides restored = new FusorSides();
        assertFalse(restored.load(tag), "the save should restore verbatim");
        assertEquals(SideType.INPUT, restored.type(Direction.NORTH));
        assertEquals(SideType.OUTPUT, restored.type(Direction.WEST));
        assertEquals(POTENTIA, restored.outputAspect());
        assertEquals(6, restored.amount(Direction.NORTH));
        assertEquals(8, restored.amount(Direction.SOUTH));
        assertEquals(1, restored.amount(Direction.WEST));
        assertEquals(sides.totalStored(), restored.totalStored(), "a reload changed the total");
        assertTrue(restored.fullyMarked());

        FusorSides empty = new FusorSides();
        assertFalse(empty.load(new CompoundTag()));
        assertTrue(empty.isEmpty());
        assertTrue(new FusorSides().save().isEmpty(), "an untouched fusor writes no tag");
    }

    @Test
    void aForgedSaveCannotExceedTheCapacityOrLeaveADeadRecipe() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        CompoundTag over = new CompoundTag();
        over.putByte("face", (byte) Direction.NORTH.get3DDataValue());
        over.putByte("type", (byte) SideType.INPUT.ordinal());
        over.putInt("amount", 5000);
        over.putString("aspect", IGNIS.serialized());
        list.add(over);
        CompoundTag aspectless = new CompoundTag();
        aspectless.putByte("face", (byte) Direction.SOUTH.get3DDataValue());
        aspectless.putByte("type", (byte) SideType.INPUT.ordinal());
        aspectless.putInt("amount", 12);
        list.add(aspectless);
        tag.put("Sides", list);

        FusorSides sides = new FusorSides();
        assertTrue(sides.load(tag), "a clamped and a dropped slot must be reported");
        assertEquals(FusorSides.MAX_AMOUNT, sides.amount(Direction.NORTH));
        assertEquals(SideType.NONE, sides.type(Direction.SOUTH), "an input with no aspect is dropped");
        assertEquals(0, sides.amount(Direction.SOUTH));
    }
}
