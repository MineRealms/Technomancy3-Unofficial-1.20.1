package theflogat.technomancy.compat.gtceu;

import theflogat.technomancy.common.energy.EnergyUnits;

/**
 * Derives a machine's EU input rating from that machine's own energy numbers, so no two machines
 * have to share a constant that only fits one of them.
 *
 * <p>Both directions of error are fatal rather than merely inefficient, which is why the rating
 * cannot be picked by feel. GTCEu rejects an over-voltage packet outright instead of throttling
 * it, so a rating below the network's voltage does not slow the machine down, it disconnects it.
 * And {@link theflogat.technomancy.common.energy.EnergyLedger#acceptPackets} refuses a packet
 * larger than the whole per-tick input budget, so a rating above that ceiling is just as
 * unreachable: the machine would answer every query with zero amperes forever.</p>
 *
 * <p>Like {@link EuTier} this class deliberately links no {@code com.gregtechceu} type, so the
 * machines that call it stay loadable on a runtime without GTCEu.</p>
 */
public final class EuRating {

    /**
     * Amperes every consumer advertises. Two rather than one so a network running below the
     * rated voltage is not capped to a single packet per tick; the machine's shared per-tick Q
     * budget stays the real ceiling either way, because {@code acceptPackets} bounds the ampere
     * count by the remaining receive budget as well.
     */
    public static final long CONSUMER_AMPS = 2;

    private EuRating() {
    }

    /**
     * Voltage a consumer should advertise.
     *
     * <p>The tier is the lowest one whose single ampere pays for the machine's dearest tick of
     * work, so one packet can always fund it. It is then stepped down while a packet at that
     * voltage would not fit {@code maxReceiveQPerTick}, because such a packet can never be
     * accepted at all.</p>
     *
     * @param peakDrawQPerTick   the most Q the machine can spend on one tick of work
     * @param maxReceiveQPerTick its per-tick input budget
     * @return the voltage, or 0 when even the lowest tier does not fit and the machine should
     *         keep its FE-only rating
     */
    public static long inputVoltage(long peakDrawQPerTick, long maxReceiveQPerTick) {
        long qPerEu = EnergyUnits.qPerEu();
        if (peakDrawQPerTick <= 0 || maxReceiveQPerTick <= 0) {
            return 0;
        }
        EuTier tier = EuTier.forVoltage(divideCeil(peakDrawQPerTick, qPerEu));
        // Compared by division, not multiplication, so a huge budget cannot overflow.
        while (tier.voltage() > maxReceiveQPerTick / qPerEu) {
            if (tier.index() == 0) {
                return 0;
            }
            tier = EuTier.byIndex(tier.index() - 1);
        }
        return tier.voltage();
    }

    /** {@code value / divisor} rounded up, without the overflow of {@code value + divisor - 1}. */
    private static long divideCeil(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
}
