package theflogat.technomancy.common.coils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class CoilLinksTest {

    private static final BlockPos COIL = new BlockPos(100, 64, -200);

    private static CoilLink at(int dx, int dy, int dz) {
        return new CoilLink(COIL.offset(dx, dy, dz), Direction.UP);
    }

    @Test
    void addsAValidLink() {
        CoilLinks links = new CoilLinks();
        assertEquals(CoilLinks.Result.ADDED, links.add(COIL, at(3, 0, 0)));
        assertEquals(1, links.size());
        assertTrue(links.contains(COIL.offset(3, 0, 0)));
    }

    @Test
    void refusesASelfLink() {
        CoilLinks links = new CoilLinks();
        assertEquals(CoilLinks.Result.SELF, links.add(COIL, at(0, 0, 0)));
        assertTrue(links.isEmpty());
    }

    @Test
    void rangeIsACubeWithInclusiveEdges() {
        CoilLinks links = new CoilLinks();
        int r = CoilLinks.MAX_RANGE;
        assertEquals(CoilLinks.Result.ADDED, links.add(COIL, at(r, -r, r)));
        assertEquals(CoilLinks.Result.OUT_OF_RANGE, links.add(COIL, at(r + 1, 0, 0)));
        assertEquals(CoilLinks.Result.OUT_OF_RANGE, links.add(COIL, at(0, -r - 1, 0)));
        assertEquals(CoilLinks.Result.OUT_OF_RANGE, links.add(COIL, at(0, 0, r + 1)));
        assertEquals(1, links.size());
    }

    @Test
    void duplicatesAreJudgedByPositionNotFace() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, new CoilLink(COIL.east(2), Direction.UP));
        assertEquals(CoilLinks.Result.DUPLICATE, links.add(COIL, new CoilLink(COIL.east(2), Direction.NORTH)));
        assertEquals(1, links.size());
    }

    @Test
    void theCapIsEnforced() {
        CoilLinks links = new CoilLinks();
        for (int i = 1; i <= CoilLinks.MAX_LINKS; i++) {
            assertEquals(CoilLinks.Result.ADDED, links.add(COIL, at(i, 0, 0)));
        }
        assertEquals(CoilLinks.Result.FULL, links.add(COIL, at(0, 1, 0)));
        // A refusal for another reason still wins over FULL, so the player is told the real problem.
        assertEquals(CoilLinks.Result.DUPLICATE, links.add(COIL, at(1, 0, 0)));
        assertEquals(CoilLinks.MAX_LINKS, links.size());
    }

    @Test
    void removeAndClear() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, at(1, 0, 0));
        links.add(COIL, at(2, 0, 0));
        assertTrue(links.remove(COIL.offset(1, 0, 0)));
        assertFalse(links.remove(COIL.offset(1, 0, 0)));
        assertEquals(1, links.clear());
        assertTrue(links.isEmpty());
    }

    @Test
    void rotationVisitsEveryLinkOnceAndMovesTheStart() {
        CoilLinks links = new CoilLinks();
        for (int i = 1; i <= 3; i++) {
            links.add(COIL, at(i, 0, 0));
        }
        Set<BlockPos> firsts = new HashSet<>();
        for (int pass = 0; pass < 3; pass++) {
            List<CoilLink> order = links.rotation();
            assertEquals(3, order.size());
            assertEquals(3, new HashSet<>(order).size());
            firsts.add(order.get(0).pos());
        }
        assertEquals(3, firsts.size(), "each link should lead one pass in three");
        assertTrue(new CoilLinks().rotation().isEmpty());
    }

    @Test
    void rotationIsASnapshot() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, at(1, 0, 0));
        links.add(COIL, at(2, 0, 0));
        List<CoilLink> order = links.rotation();
        for (CoilLink link : order) {
            links.remove(link.pos());
        }
        assertTrue(links.isEmpty());
    }

    @Test
    void saveLoadRoundTripKeepsPositionsFacesAndOrder() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, new CoilLink(COIL.offset(5, -3, 7), Direction.WEST));
        links.add(COIL, new CoilLink(COIL.offset(-1, 0, 0), null));
        CompoundTag saved = links.save();
        assertEquals(CoilLinks.VERSION, saved.getInt("version"));

        CoilLinks loaded = new CoilLinks();
        assertEquals(0, loaded.load(saved, COIL));
        assertEquals(links.view(), loaded.view());
        assertNull(loaded.view().get(1).face());
    }

    @Test
    void loadReappliesEveryRule() {
        CompoundTag saved = new CompoundTag();
        ListTag list = new ListTag();
        list.add(entry(COIL));                                   // self
        list.add(entry(COIL.east(CoilLinks.MAX_RANGE + 1)));      // out of range
        list.add(entry(COIL.east()));                            // valid
        list.add(entry(COIL.east()));                            // duplicate
        for (int i = 2; i <= CoilLinks.MAX_LINKS + 1; i++) {      // one past the cap
            list.add(entry(COIL.north(i)));
        }
        saved.put("links", list);

        CoilLinks loaded = new CoilLinks();
        assertEquals(4, loaded.load(saved, COIL));
        assertEquals(CoilLinks.MAX_LINKS, loaded.size());
        assertFalse(loaded.contains(COIL));
    }

    @Test
    void loadingNothingLeavesItEmpty() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, at(1, 0, 0));
        assertEquals(0, links.load(null, COIL));
        assertTrue(links.isEmpty());
        assertEquals(0, links.load(new CompoundTag(), COIL));
    }

    /** A link saved by a coil is judged against the coil it is loaded into, not a stale origin. */
    @Test
    void loadJudgesRangeFromTheLoadingCoil() {
        CoilLinks links = new CoilLinks();
        links.add(COIL, at(CoilLinks.MAX_RANGE, 0, 0));
        CoilLinks moved = new CoilLinks();
        assertEquals(1, moved.load(links.save(), COIL.west()));
    }

    private static CompoundTag entry(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }
}
