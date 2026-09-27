package theflogat.technomancy.common.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectId;

/**
 * Hands essentia from a store to one neighbour and debits exactly what the neighbour accepted.
 *
 * <p>This exists because getting it wrong is the worst defect in the block being ported.
 * 1.7.10's condenser wrote {@code amount = neighbour.addEssentia(aspect, amount, dir)}, reading
 * the returned "amount accepted" as if it were "amount left over": a neighbour that took
 * everything left the buffer untouched and duplicated the essentia, one that took nothing
 * zeroed the buffer and destroyed it. The semantics were exactly inverted
 * (defect A-9). The contract here is the TC4R one — the sink reports what it
 * <em>accepted</em>, in {@code [0, offered]} — and the store is debited by that and nothing
 * else.</p>
 *
 * <p>Ordering hazard, handled the same way {@code EuPush} handles it for energy: the offered
 * units leave the store <em>before</em> the sink is called. A neighbour that calls back into
 * this machine while accepting therefore cannot be shown the same units twice. Whatever it
 * refuses is put straight back, including on an exception.</p>
 */
public final class EssentiaPush {

    /** A neighbour that receives essentia. @see #push */
    @FunctionalInterface
    public interface Sink {
        /**
         * @param aspect what is on offer
         * @param amount units offered; always positive
         * @return units actually accepted; the caller clamps this to {@code [0, amount]}
         *         because a foreign endpoint may report anything
         */
        int accept(AspectId aspect, int amount);
    }

    private EssentiaPush() {
    }

    /**
     * Offers up to {@code maxAmount} units of one aspect and settles with what the sink took.
     *
     * @return units the sink accepted and the store gave up; the two are the same number by
     *         construction, which is the whole point of this method
     * @throws IllegalStateException if the store refuses to take back what the sink declined,
     *                               which would mean essentia had been destroyed in transit
     */
    public static int push(EssentiaStore store, AspectId aspect, int maxAmount, Sink sink) {
        int offered = Math.min(Math.max(0, maxAmount), store.amount(aspect));
        if (offered <= 0) {
            return 0;
        }
        int reserved = store.take(aspect, offered, false);
        if (reserved <= 0) {
            return 0;
        }
        int accepted = 0;
        try {
            accepted = Math.max(0, Math.min(sink.accept(aspect, reserved), reserved));
        } finally {
            int refund = reserved - accepted;
            if (refund > 0 && store.add(aspect, refund, false) != refund) {
                throw new IllegalStateException("essentia lost in transit: " + refund + " " + aspect
                        + " could not be returned to a store that had just released it");
            }
        }
        return accepted;
    }
}
