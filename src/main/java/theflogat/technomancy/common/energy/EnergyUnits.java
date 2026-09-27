package theflogat.technomancy.common.energy;

/**
 * The exchange rate between the internal unit Q (= 1 FE) and GT EU.
 *
 * <p>The rate is part of the save economy: changing it changes the EU value of every stored
 * balance. It is therefore read once during common setup and frozen for the rest of the
 * session; later config reloads do not affect it.</p>
 */
public final class EnergyUnits {

    public static final long DEFAULT_Q_PER_EU = 4;

    private static volatile long qPerEu = DEFAULT_Q_PER_EU;
    private static volatile boolean frozen;

    private EnergyUnits() {
    }

    /** Q per EU; always a positive integer. */
    public static long qPerEu() {
        return qPerEu;
    }

    /** Whole EU represented by {@code q}; the remainder stays in the ledger as Q. */
    public static long toEu(long q) {
        return q / qPerEu;
    }

    /**
     * Fixes the rate for this session. Only the first call has an effect.
     *
     * @return the rate in effect after the call
     */
    public static synchronized long freeze(long value) {
        if (!frozen) {
            if (value <= 0) {
                throw new IllegalArgumentException("Q per EU must be positive: " + value);
            }
            qPerEu = value;
            frozen = true;
        }
        return qPerEu;
    }
}
