package theflogat.technomancy.compat.gtceu;

import theflogat.technomancy.common.energy.EnergyLedger;

/**
 * Active EU output, expressed without any {@code com.gregtechceu} type so the reservation and
 * refund logic can be unit tested while the GT API is {@code compileOnly}.
 * {@link GtceuEnergyProtocol} supplies the real {@code IEnergyContainer} call as a
 * {@link PacketSink}.
 */
public final class EuPush {

    /**
     * A neighbour that receives EU packets.
     *
     * @see #push
     */
    @FunctionalInterface
    public interface PacketSink {
        /**
         * @param voltage  EU per ampere, the sender's rated output voltage
         * @param amperage whole packets offered
         * @return amperes the receiver claims to have used; the caller clamps this to
         *         {@code [0, amperage]} because a foreign receiver may report anything
         */
        long accept(long voltage, long amperage);
    }

    private EuPush() {
    }

    /**
     * Offers whole packets at the ledger's rated output voltage and settles with the amperes the
     * receiver reports.
     *
     * <p>The energy and the tick's ampere quota leave the ledger <em>before</em> the sink is
     * called, so a receiver that calls back into this machine while accepting cannot spend the
     * same energy twice. Everything the receiver does not take is refunded, including the unused
     * amperes; on an exception nothing stays spent.</p>
     *
     * @return Q actually sent, which is 0 when no whole packet could be offered or nothing was taken
     */
    public static long push(EnergyLedger ledger, long qPerEu, long gameTime, PacketSink sink) {
        // Read before reserving so the offer and the sender's advertised output voltage cannot
        // disagree: reservePackets sizes its packets with the same value.
        long voltage = ledger.limits().euOutputVoltage();
        try (EnergyLedger.Reservation packets = ledger.reservePackets(qPerEu, gameTime)) {
            long offered = packets.amps();
            if (offered <= 0) {
                return 0;
            }
            long reported = sink.accept(voltage, offered);
            long used = packets.commitAmps(Math.max(0, Math.min(reported, offered)));
            return used * packets.qPerAmp();
        }
    }
}
