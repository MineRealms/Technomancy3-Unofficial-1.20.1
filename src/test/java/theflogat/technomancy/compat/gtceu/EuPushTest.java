package theflogat.technomancy.compat.gtceu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.energy.EnergyLedger;
import theflogat.technomancy.common.energy.EnergyLimits;

class EuPushTest {

    private static final long R = 4; // Q per EU used throughout

    /** 100k Q, LV output at two amperes: one packet is 32 EU = 128 Q. */
    private static EnergyLedger generator(long stored) {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 0, 100_000).withEuOutput(32, 2));
        ledger.generate(stored);
        return ledger;
    }

    @Test
    void offersWholePacketsAtTheRatedVoltage() {
        EnergyLedger ledger = generator(100_000);
        AtomicLong seenVoltage = new AtomicLong();
        AtomicLong seenAmps = new AtomicLong();
        long sent = EuPush.push(ledger, R, 0, (voltage, amperage) -> {
            seenVoltage.set(voltage);
            seenAmps.set(amperage);
            return amperage;
        });
        assertEquals(32, seenVoltage.get());
        assertEquals(2, seenAmps.get(), "bounded by the rated output amperage");
        assertEquals(2 * 32 * R, sent);
        assertEquals(100_000 - 2 * 32 * R, ledger.stored());
    }

    @Test
    void partialAcceptanceRefundsBalanceAndAmpereQuota() {
        EnergyLedger ledger = generator(100_000);
        assertEquals(32 * R, EuPush.push(ledger, R, 0, (voltage, amperage) -> 1));
        assertEquals(100_000 - 32 * R, ledger.stored(), "only the accepted packet left the ledger");
        // The refunded ampere is available again on the same tick, e.g. for the next output face.
        assertEquals(32 * R, EuPush.push(ledger, R, 0, (voltage, amperage) -> amperage));
        assertEquals(100_000 - 2 * 32 * R, ledger.stored());
        assertEquals(0, EuPush.push(ledger, R, 0, (voltage, amperage) -> amperage), "quota spent");
    }

    @Test
    void aReceiverCannotReportMoreThanWasOffered() {
        EnergyLedger ledger = generator(100_000);
        assertEquals(2 * 32 * R, EuPush.push(ledger, R, 0, (voltage, amperage) -> Long.MAX_VALUE));
        assertEquals(100_000 - 2 * 32 * R, ledger.stored());
    }

    @Test
    void aNegativeOrZeroReportSpendsNothing() {
        EnergyLedger ledger = generator(100_000);
        assertEquals(0, EuPush.push(ledger, R, 0, (voltage, amperage) -> -5));
        assertEquals(0, EuPush.push(ledger, R, 1, (voltage, amperage) -> 0));
        assertEquals(100_000, ledger.stored());
    }

    @Test
    void aReentrantReceiverCannotSpendTheSameEnergyTwice() {
        EnergyLedger ledger = generator(256);
        long sent = EuPush.push(ledger, R, 0, (voltage, amperage) -> {
            // The neighbour calls back into this machine while it is being served.
            assertEquals(0, ledger.extract(256, 0, false), "reserved energy is already out of the balance");
            try (EnergyLedger.Reservation nested = ledger.reservePackets(R, 0)) {
                assertEquals(0, nested.amps(), "and cannot be offered a second time");
            }
            return amperage;
        });
        assertEquals(256, sent);
        assertEquals(0, ledger.stored(), "exactly the offered energy left, never twice");
    }

    @Test
    void nothingIsOfferedWithoutWholePacketsOrAnEuOutput() {
        EnergyLedger noEu = new EnergyLedger(EnergyLimits.fe(1000, 0, 1000));
        noEu.generate(1000);
        assertEquals(0, EuPush.push(noEu, R, 0, (voltage, amperage) -> {
            throw new AssertionError("a machine without EU output must not call a receiver");
        }));
        assertEquals(1000, noEu.stored());

        EnergyLedger tooLittle = generator(127);
        assertEquals(0, EuPush.push(tooLittle, R, 0, (voltage, amperage) -> {
            throw new AssertionError("127 Q is less than one 128 Q packet");
        }));
        assertEquals(127, tooLittle.stored());
    }

    @Test
    void aThrowingReceiverLeavesTheBalanceUntouched() {
        EnergyLedger ledger = generator(100_000);
        assertThrows(IllegalStateException.class, () -> EuPush.push(ledger, R, 0, (voltage, amperage) -> {
            throw new IllegalStateException("neighbour blew up");
        }));
        assertEquals(100_000, ledger.stored(), "the reservation was refunded, not committed");
        assertEquals(2 * 32 * R, EuPush.push(ledger, R, 0, (voltage, amperage) -> amperage),
                "and the ampere quota came back too");
    }

    @Test
    void theOutputBudgetIsSharedAcrossFaces() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 0, 160).withEuOutput(32, 8));
        ledger.generate(100_000);
        assertEquals(128, EuPush.push(ledger, R, 0, (voltage, amperage) -> amperage),
                "160 Q of output budget holds exactly one 128 Q packet");
        assertEquals(0, EuPush.push(ledger, R, 0, (voltage, amperage) -> amperage));
        assertTrue(ledger.stored() == 100_000 - 128);
    }
}
