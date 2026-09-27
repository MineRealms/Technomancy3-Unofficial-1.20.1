package theflogat.technomancy.common.energy;

/**
 * Static limits of one {@link EnergyLedger}, all in the internal unit Q (1 Q = 1 FE).
 *
 * <p>Per-tick budgets are shared by every protocol view of the same ledger: FE and EU
 * input both draw from {@code maxReceivePerTick}, and an EU packet additionally counts
 * one ampere against {@code euInputAmps}. A voltage or ampere value of zero disables
 * that EU direction.</p>
 */
public record EnergyLimits(
        long capacity,
        long maxReceivePerTick,
        long maxExtractPerTick,
        long euInputVoltage,
        long euInputAmps,
        long euOutputVoltage,
        long euOutputAmps) {

    public EnergyLimits {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        requireNonNegative(maxReceivePerTick, "maxReceivePerTick");
        requireNonNegative(maxExtractPerTick, "maxExtractPerTick");
        requireNonNegative(euInputVoltage, "euInputVoltage");
        requireNonNegative(euInputAmps, "euInputAmps");
        requireNonNegative(euOutputVoltage, "euOutputVoltage");
        requireNonNegative(euOutputAmps, "euOutputAmps");
    }

    /** Limits for a store that speaks FE only. */
    public static EnergyLimits fe(long capacity, long maxReceivePerTick, long maxExtractPerTick) {
        return new EnergyLimits(capacity, maxReceivePerTick, maxExtractPerTick, 0, 0, 0, 0);
    }

    public EnergyLimits withEuInput(long voltage, long amps) {
        return new EnergyLimits(capacity, maxReceivePerTick, maxExtractPerTick, voltage, amps, euOutputVoltage, euOutputAmps);
    }

    public EnergyLimits withEuOutput(long voltage, long amps) {
        return new EnergyLimits(capacity, maxReceivePerTick, maxExtractPerTick, euInputVoltage, euInputAmps, voltage, amps);
    }

    public boolean acceptsEu() {
        return euInputVoltage > 0 && euInputAmps > 0;
    }

    public boolean emitsEu() {
        return euOutputVoltage > 0 && euOutputAmps > 0;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
