package theflogat.technomancy.common.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class EssentiaStoreTest {

    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId AQUA = AspectId.parse("aqua");
    private static final AspectId ORDO = AspectId.parse("ordo");

    @Test
    void jarHoldsOneAspectUpToItsCapacity() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        assertEquals(64, store.add(IGNIS, 100, false), "capacity bounds the insert");
        assertEquals(64, store.amount(IGNIS));
        assertEquals(64, store.total());
        assertEquals(0, store.add(IGNIS, 1, false), "full");
        assertEquals(0, store.add(AQUA, 10, false), "the single aspect slot is taken");
        assertFalse(store.accepts(AQUA));
        assertEquals(Set.of(IGNIS), store.aspects());
    }

    @Test
    void emptyingReleasesTheAspectSlot() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        store.add(IGNIS, 10, false);
        assertEquals(10, store.take(IGNIS, 25, false), "partial take yields what is there");
        assertEquals(0, store.amount(IGNIS));
        assertTrue(store.isEmpty());
        assertEquals(Set.of(), store.aspects(), "a zero amount is not a stored aspect");
        assertEquals(10, store.add(AQUA, 10, false), "the slot is free again");
    }

    @Test
    void nonPositiveAndUnknownRequestsDoNothing() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        store.add(IGNIS, 10, false);
        assertEquals(0, store.add(IGNIS, 0, false));
        assertEquals(0, store.add(IGNIS, -5, false));
        assertEquals(0, store.add(null, 5, false));
        assertEquals(0, store.take(IGNIS, 0, false));
        assertEquals(0, store.take(IGNIS, Integer.MIN_VALUE, false));
        assertEquals(0, store.take(AQUA, 5, false), "taking an absent aspect");
        assertEquals(10, store.total());
    }

    @Test
    void simulationChangesNothing() {
        AtomicInteger changes = new AtomicInteger();
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 4));
        store.add(IGNIS, 30, false);
        store.setListener(changes::incrementAndGet);
        for (int i = 0; i < 3; i++) {
            assertEquals(34, store.add(IGNIS, 50, true), "bounded by the per-aspect capacity");
            assertEquals(30, store.take(IGNIS, 50, true));
            assertTrue(store.takeExact(IGNIS, 30, true));
        }
        assertEquals(30, store.amount(IGNIS));
        assertEquals(0, changes.get());
        assertEquals(34, store.add(IGNIS, 50, false));
        assertEquals(1, changes.get());
    }

    @Test
    void pooledStoreSharesOneTotalAcrossAspects() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 100, 3));
        assertEquals(64, store.add(IGNIS, 64, false));
        assertEquals(36, store.add(AQUA, 64, false), "the shared pool, not the per-aspect limit, binds");
        assertEquals(100, store.total());
        assertEquals(0, store.add(ORDO, 1, false));
        assertTrue(store.accepts(ORDO), "a third slot exists, it is the pool that is full");
        assertEquals(0, store.space(ORDO));
        assertEquals(10, store.take(IGNIS, 10, false));
        assertEquals(10, store.space(ORDO), "freeing pool space re-opens the third aspect");
    }

    @Test
    void aspectSlotsAreLimited() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 1000, 2));
        assertEquals(5, store.add(IGNIS, 5, false));
        assertEquals(5, store.add(AQUA, 5, false));
        assertEquals(0, store.add(ORDO, 5, false), "no third slot");
        assertFalse(store.accepts(ORDO));
        assertEquals(0, store.space(ORDO));
        store.take(AQUA, 5, false);
        assertEquals(5, store.add(ORDO, 5, false), "the freed slot is reusable");
        assertEquals(Set.of(IGNIS, ORDO), store.aspects());
    }

    @Test
    void voidStoreAcceptsEverythingAndKeepsOnlyItsCapacity() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64).withVoidOverflow(true));
        assertEquals(100, store.add(IGNIS, 100, false), "a void jar reports the full amount as taken");
        assertEquals(64, store.amount(IGNIS), "only the capacity is kept");
        assertEquals(50, store.add(IGNIS, 50, false), "still accepts when full");
        assertEquals(64, store.amount(IGNIS));
        assertEquals(64, store.total());
    }

    @Test
    void takeExactIsAllOrNothing() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        store.add(IGNIS, 10, false);
        assertFalse(store.takeExact(IGNIS, 11, false), "not enough: nothing moves");
        assertEquals(10, store.amount(IGNIS));
        assertFalse(store.takeExact(AQUA, 1, false));
        assertFalse(store.takeExact(IGNIS, 0, false), "a zero drain is not a transfer");
        assertTrue(store.takeExact(IGNIS, 10, false));
        assertTrue(store.isEmpty());
    }

    @Test
    void filtersDecideWhatMayEnterWithoutBeingContents() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        assertTrue(store.addFilter(IGNIS));
        assertFalse(store.addFilter(IGNIS), "idempotent");
        assertFalse(store.addFilter(AQUA), "only one aspect slot, so only one filter");

        assertEquals(0, store.add(AQUA, 10, false), "filtered out");
        assertFalse(store.accepts(AQUA));
        assertEquals(10, store.add(IGNIS, 10, false));

        // A filter is an identity, never a phantom amount.
        assertEquals(0, store.visibleAspects().amount(AQUA));
        store.clearContents();
        assertTrue(store.visibleAspects().amounts().isEmpty());
        assertEquals(Set.of(IGNIS), store.filters(), "clearing contents keeps the label");
        assertEquals(List.of(IGNIS), store.visibleAspectOrder(), "an empty labelled store still shows its label");

        assertTrue(store.clearFilters());
        assertFalse(store.clearFilters());
        assertEquals(10, store.add(AQUA, 10, false), "unfiltered accepts anything");
    }

    @Test
    void settingMoreFiltersThanSlotsIsRejected() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 2));
        assertTrue(store.setFilters(List.of(IGNIS, AQUA)));
        assertFalse(store.setFilters(List.of(IGNIS, AQUA)), "no change");
        assertThrows(IllegalArgumentException.class, () -> store.setFilters(List.of(IGNIS, AQUA, ORDO)));
        assertEquals(Set.of(IGNIS, AQUA), store.filters(), "the rejected call changed nothing");
    }

    @Test
    void dominantAspectPrefersTheLargestThenTheOldest() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 3));
        assertNull(store.dominantAspect());
        store.add(IGNIS, 5, false);
        store.add(AQUA, 5, false);
        assertEquals(IGNIS, store.dominantAspect(), "tie goes to the first inserted");
        store.add(AQUA, 3, false);
        assertEquals(AQUA, store.dominantAspect());
        store.take(AQUA, 8, false);
        assertEquals(IGNIS, store.dominantAspect());
    }

    @Test
    void visibleAspectsIsAnImmutableSnapshot() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 3));
        store.add(IGNIS, 7, false);
        var snapshot = store.visibleAspects();
        assertEquals(7, snapshot.amount(IGNIS));
        store.add(IGNIS, 5, false);
        assertEquals(7, snapshot.amount(IGNIS), "the snapshot does not follow later changes");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.amounts().put(AQUA, 1));
        assertThrows(UnsupportedOperationException.class, () -> store.aspects().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.filters().add(AQUA));
    }

    @Test
    void contentsAndFiltersSurviveARoundTrip() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 3));
        store.add(IGNIS, 12, false);
        store.add(AQUA, 34, false);
        store.setFilters(List.of(IGNIS, ORDO));

        EssentiaStore restored = new EssentiaStore(EssentiaLimits.pooled(64, 256, 3));
        assertFalse(restored.load(store.save()), "a faithful save restores verbatim");
        assertEquals(12, restored.amount(IGNIS));
        assertEquals(34, restored.amount(AQUA));
        assertEquals(46, restored.total());
        assertEquals(Set.of(IGNIS, ORDO), restored.filters());
        assertEquals(List.of(IGNIS, AQUA, ORDO), restored.visibleAspectOrder());
    }

    @Test
    void loadingAnEmptyOrForeignTagYieldsAnEmptyStore() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        store.add(IGNIS, 10, false);
        assertFalse(store.load(new CompoundTag()), "a fresh block entity is not a lossy load");
        assertTrue(store.isEmpty());

        CompoundTag future = new CompoundTag();
        future.putInt("v", 99);
        store.add(IGNIS, 10, false);
        assertTrue(store.load(future), "an unknown schema is reported, not guessed at");
        assertTrue(store.isEmpty());
    }

    @Test
    void loadClampsAndDropsWhatNoLongerFits() {
        EssentiaStore big = new EssentiaStore(EssentiaLimits.pooled(500, 1000, 3));
        big.add(IGNIS, 400, false);
        big.add(AQUA, 400, false);
        big.add(ORDO, 200, false);
        CompoundTag saved = big.save();

        EssentiaStore small = new EssentiaStore(EssentiaLimits.pooled(64, 100, 2));
        assertTrue(small.load(saved), "shrunken limits cannot restore verbatim");
        assertEquals(64, small.amount(IGNIS), "clamped to the per-aspect capacity");
        assertEquals(36, small.amount(AQUA), "clamped to the remaining pool");
        assertEquals(0, small.amount(ORDO), "no third aspect slot");
        assertEquals(100, small.total());
        assertTrue(small.total() <= small.totalCapacity());
    }

    @Test
    void loadIgnoresMalformedEntries() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 1);
        ListTag aspects = new ListTag();
        aspects.add(entry("ignis", 5));
        aspects.add(entry("not a valid id", 5));
        aspects.add(entry("", 5));
        aspects.add(entry("ordo", 0));
        aspects.add(entry("aqua", -3));
        tag.put("aspects", aspects);

        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 4));
        assertTrue(store.load(tag), "dropped entries are reported");
        assertEquals(5, store.amount(IGNIS));
        assertEquals(5, store.total());
        assertEquals(Set.of(IGNIS), store.aspects());
    }

    private static CompoundTag entry(String id, int amount) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id);
        entry.putInt("n", amount);
        return entry;
    }

    @Test
    void shrinkingLimitsKeepsExistingContents() {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.pooled(64, 256, 3));
        store.add(IGNIS, 64, false);
        store.setLimits(EssentiaLimits.jar(10));
        assertEquals(64, store.amount(IGNIS), "nothing is destroyed");
        assertEquals(0, store.space(IGNIS), "it just stops accepting");
        assertEquals(0, store.add(IGNIS, 1, false));
        assertEquals(64, store.take(IGNIS, 1000, false), "and it can still be drained");
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EssentiaLimits.jar(0));
        assertThrows(IllegalArgumentException.class, () -> EssentiaLimits.pooled(64, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> EssentiaLimits.pooled(64, 64, 0));
        assertEquals(64, EssentiaLimits.pooled(1000, 64, 1).effectivePerAspectCapacity(),
                "a per-aspect limit above the pool cannot be reached");
    }
}
