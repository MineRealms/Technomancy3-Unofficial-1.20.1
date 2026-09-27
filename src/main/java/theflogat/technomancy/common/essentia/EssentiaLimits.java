package theflogat.technomancy.common.essentia;

/**
 * Static shape of an {@link EssentiaStore}: how much of one aspect fits, how much fits in
 * total, how many distinct aspects may be held at once, and whether overflow is deleted.
 *
 * <p>TC4's single-aspect warded jar is {@code jar(64)}; a multi-aspect store such as the
 * quantum jar is {@code pooled(...)}. All amounts are essentia units, matching the
 * {@code int} quantities of the TC4R essentia API.</p>
 */
public record EssentiaLimits(int perAspectCapacity, int totalCapacity, int maxDistinctAspects,
        boolean voidOverflow) {

    public EssentiaLimits {
        if (perAspectCapacity <= 0) {
            throw new IllegalArgumentException("per-aspect capacity must be positive: " + perAspectCapacity);
        }
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("total capacity must be positive: " + totalCapacity);
        }
        if (maxDistinctAspects <= 0) {
            throw new IllegalArgumentException("aspect slots must be positive: " + maxDistinctAspects);
        }
    }

    /** One aspect, TC4 jar semantics. */
    public static EssentiaLimits jar(int capacity) {
        return new EssentiaLimits(capacity, capacity, 1, false);
    }

    /**
     * Several aspects sharing one pool. {@code perAspectCapacity} above {@code totalCapacity}
     * simply means a single aspect may fill the whole pool.
     */
    public static EssentiaLimits pooled(int perAspectCapacity, int totalCapacity, int maxDistinctAspects) {
        return new EssentiaLimits(perAspectCapacity, totalCapacity, maxDistinctAspects, false);
    }

    /** Void variant: input is always accepted, anything past the capacity is destroyed. */
    public EssentiaLimits withVoidOverflow(boolean voidOverflow) {
        return new EssentiaLimits(perAspectCapacity, totalCapacity, maxDistinctAspects, voidOverflow);
    }

    /** The per-aspect limit that can actually be reached, i.e. capped by the shared pool. */
    public int effectivePerAspectCapacity() {
        return Math.min(perAspectCapacity, totalCapacity);
    }
}
