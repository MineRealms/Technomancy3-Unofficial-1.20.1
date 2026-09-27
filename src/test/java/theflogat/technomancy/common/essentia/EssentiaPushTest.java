package theflogat.technomancy.common.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Defect A-9 in one file: 1.7.10's condenser assigned the "amount accepted" that
 * {@code addEssentia} returns straight into its own stored amount, as if it were the amount
 * left over. A neighbour that took everything therefore left the buffer untouched and the
 * essentia was duplicated; one that took nothing zeroed the buffer and it was destroyed.
 *
 * <p>Every test here states the same property from a different angle: the total essentia in
 * the system — the store plus the receiver — does not change across a push.</p>
 */
class EssentiaPushTest {

    private static final AspectId POTENTIA = AspectId.parse("potentia");
    private static final AspectId IGNIS = AspectId.parse("ignis");

    /** A neighbour with a fixed appetite, which records everything it is handed. */
    private static final class Receiver implements EssentiaPush.Sink {
        private final int capacity;
        private int held;
        private int lastOffer;
        private int calls;

        Receiver(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int accept(AspectId aspect, int amount) {
            calls++;
            lastOffer = amount;
            int taken = Math.min(amount, capacity - held);
            held += taken;
            return taken;
        }
    }

    private static EssentiaStore store(int amount) {
        EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(64));
        store.add(POTENTIA, amount, false);
        return store;
    }

    @Test
    void aReceiverThatTakesEverythingIsDebitedInFull() {
        EssentiaStore store = store(40);
        Receiver receiver = new Receiver(64);
        assertEquals(40, EssentiaPush.push(store, POTENTIA, 40, receiver));
        assertEquals(40, receiver.held);
        assertEquals(0, store.total(), "the original left the buffer full here and duplicated it");
        assertEquals(40, store.total() + receiver.held, "total essentia changed");
    }

    @Test
    void aReceiverThatTakesNothingCostsNothing() {
        EssentiaStore store = store(40);
        Receiver receiver = new Receiver(0);
        assertEquals(0, EssentiaPush.push(store, POTENTIA, 40, receiver));
        assertEquals(0, receiver.held);
        assertEquals(40, store.total(), "the original zeroed the buffer here and destroyed it");
        assertEquals(40, store.total() + receiver.held, "total essentia changed");
    }

    @Test
    void aReceiverThatTakesSomeIsDebitedExactlyThatMuch() {
        EssentiaStore store = store(40);
        Receiver receiver = new Receiver(15);
        assertEquals(15, EssentiaPush.push(store, POTENTIA, 40, receiver));
        assertEquals(15, receiver.held);
        assertEquals(25, store.total());
        assertEquals(40, store.total() + receiver.held, "total essentia changed");
    }

    /** The same property swept across every possible appetite, including both extremes. */
    @Test
    void totalEssentiaIsConservedForEveryPossibleAppetite() {
        for (int appetite = 0; appetite <= 64; appetite++) {
            EssentiaStore store = store(64);
            Receiver receiver = new Receiver(appetite);
            int moved = EssentiaPush.push(store, POTENTIA, 64, receiver);
            assertEquals(appetite, moved, "appetite " + appetite);
            assertEquals(64, store.total() + receiver.held, "appetite " + appetite + " lost or made essentia");
            assertEquals(moved, receiver.held, "appetite " + appetite);
        }
    }

    @Test
    void theOfferIsBoundedByTheStoreAndByTheRequest() {
        EssentiaStore store = store(10);
        Receiver receiver = new Receiver(64);
        EssentiaPush.push(store, POTENTIA, 100, receiver);
        assertEquals(10, receiver.lastOffer, "never offer more than is held");

        EssentiaStore other = store(64);
        Receiver limited = new Receiver(64);
        EssentiaPush.push(other, POTENTIA, 5, limited);
        assertEquals(5, limited.lastOffer, "never offer more than was requested");
        assertEquals(59, other.total());
    }

    @Test
    void nothingToOfferMeansTheNeighbourIsNotEvenCalled() {
        EssentiaStore empty = new EssentiaStore(EssentiaLimits.jar(64));
        Receiver receiver = new Receiver(64);
        assertEquals(0, EssentiaPush.push(empty, POTENTIA, 10, receiver));
        assertEquals(0, receiver.calls);

        EssentiaStore wrongAspect = store(40);
        assertEquals(0, EssentiaPush.push(wrongAspect, IGNIS, 10, receiver));
        assertEquals(0, receiver.calls, "an aspect the store does not hold is not an offer");
        assertEquals(40, wrongAspect.total());

        assertEquals(0, EssentiaPush.push(wrongAspect, POTENTIA, 0, receiver));
        assertEquals(0, receiver.calls);
    }

    /** A foreign endpoint may report anything; the store must not follow it out of range. */
    @Test
    void anOverReportingOrNegativeReceiverIsClamped() {
        EssentiaStore store = store(40);
        assertEquals(20, EssentiaPush.push(store, POTENTIA, 20, (aspect, amount) -> Integer.MAX_VALUE));
        assertEquals(20, store.total(), "an inflated report must not debit more than was offered");

        assertEquals(0, EssentiaPush.push(store, POTENTIA, 20, (aspect, amount) -> -5));
        assertEquals(20, store.total(), "a negative report must not credit the store");
    }

    /**
     * Ordering hazard: the offered units leave the store before the neighbour is called, so a
     * neighbour that re-enters this machine cannot be shown the same units twice.
     */
    @Test
    void theOfferIsAlreadyOutOfTheStoreWhileTheNeighbourRuns() {
        EssentiaStore store = store(40);
        AtomicInteger seen = new AtomicInteger(-1);
        assertEquals(10, EssentiaPush.push(store, POTENTIA, 10, (aspect, amount) -> {
            seen.set(store.total());
            return amount;
        }));
        assertEquals(30, seen.get(), "the neighbour could still see the units it was being offered");
        assertEquals(30, store.total());
    }

    @Test
    void aThrowingNeighbourLeavesTheStoreExactlyAsItWas() {
        EssentiaStore store = store(40);
        assertThrows(IllegalStateException.class, () -> EssentiaPush.push(store, POTENTIA, 40,
                (aspect, amount) -> {
                    throw new IllegalStateException("neighbour exploded");
                }));
        assertEquals(40, store.total(), "a failed push must not cost anything");
    }

    /** The refund is checked, not assumed: losing it would be silent destruction. */
    @Test
    void refusingTheRefundIsReportedRatherThanSwallowed() {
        EssentiaStore store = store(40);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EssentiaPush.push(store, POTENTIA, 40, (aspect, amount) -> {
                    // Shrink the store under the push so the refund no longer fits.
                    store.setLimits(EssentiaLimits.jar(1));
                    return 0;
                }));
        assertTrue(failure.getMessage().contains("lost in transit"), failure.getMessage());
    }
}
