package theflogat.technomancy.common.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnergyLedgerTest {

    private static final long R = 4; // Q per EU used throughout

    private static EnergyLedger ledger(long capacity, long in, long out) {
        return new EnergyLedger(EnergyLimits.fe(capacity, in, out));
    }

    @Test
    void receiveIsBoundedByCapacityAndBudget() {
        EnergyLedger ledger = ledger(1000, 100, 100);
        assertEquals(100, ledger.receive(250, 0, false));
        assertEquals(0, ledger.receive(1, 0, false), "budget spent for tick 0");
        assertEquals(100, ledger.receive(250, 1, false), "budget resets on the next tick");
        ledger.generate(799);
        assertEquals(1, ledger.receive(100, 2, false), "capacity - 1 accepts exactly one");
        assertEquals(0, ledger.receive(100, 3, false), "full");
        assertEquals(1000, ledger.stored());
    }

    @Test
    void nonPositiveRequestsDoNothing() {
        EnergyLedger ledger = ledger(1000, 100, 100);
        ledger.generate(500);
        assertEquals(0, ledger.receive(0, 0, false));
        assertEquals(0, ledger.receive(-5, 0, false));
        assertEquals(0, ledger.extract(0, 0, false));
        assertEquals(0, ledger.extract(Long.MIN_VALUE, 0, false));
        assertEquals(0, ledger.acceptPackets(-32, 1, R, 0, false));
        assertEquals(0, ledger.acceptPackets(32, 0, R, 0, false));
        assertEquals(500, ledger.stored());
    }

    @Test
    void simulationChangesNothingIncludingBudgets() {
        AtomicInteger changes = new AtomicInteger();
        EnergyLedger ledger = ledger(1000, 100, 100);
        ledger.generate(500);
        ledger.setListener(changes::incrementAndGet);
        for (int i = 0; i < 5; i++) {
            assertEquals(100, ledger.receive(1000, 7, true));
            assertEquals(100, ledger.extract(1000, 7, true));
        }
        assertEquals(500, ledger.stored());
        assertEquals(0, changes.get());
        assertEquals(100, ledger.receive(1000, 7, false), "execution after simulation settles once");
        assertEquals(0, ledger.receive(1000, 7, true));
        assertEquals(600, ledger.stored());
    }

    @Test
    void extractSharesOneBudgetPerTick() {
        EnergyLedger ledger = ledger(1000, 0, 60);
        ledger.generate(1000);
        assertEquals(40, ledger.extract(40, 5, false));
        assertEquals(20, ledger.extract(40, 5, false));
        assertEquals(0, ledger.extract(40, 5, false));
        assertEquals(60, ledger.extract(100, 6, false));
        assertEquals(880, ledger.stored());
    }

    @Test
    void euAcceptsWholePacketsOnly() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 1000, 0).withEuInput(32, 2));
        // One LV packet = 32 EU = 128 Q.
        assertEquals(2, ledger.acceptPackets(32, 4, R, 0, false), "ampere limit");
        assertEquals(256, ledger.stored());
        assertEquals(0, ledger.acceptPackets(32, 1, R, 0, false), "no amperes left this tick");
        ledger.generate(1000);
        assertEquals(1000, ledger.stored());
        assertEquals(0, ledger.acceptPackets(1, 1, R, 1, false), "even 4 Q does not fit");

        EnergyLedger nearlyFull = new EnergyLedger(EnergyLimits.fe(1000, 1000, 0).withEuInput(32, 8));
        nearlyFull.generate(1000 - 127);
        assertEquals(0, nearlyFull.acceptPackets(32, 1, R, 0, false), "127 Q free < one 128 Q packet");
        assertEquals(873, nearlyFull.stored());
    }

    @Test
    void overVoltageIsRejectedNotTruncated() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 100_000, 0).withEuInput(32, 4));
        assertEquals(0, ledger.acceptPackets(33, 1, R, 0, false));
        assertEquals(0, ledger.acceptPackets(128, 1, R, 0, false));
        assertEquals(0, ledger.stored());
        assertEquals(1, ledger.acceptPackets(31, 1, R, 0, false), "cable loss below rating is fine");
        assertEquals(124, ledger.stored());
    }

    @Test
    void euWithoutEuInputIsRejected() {
        EnergyLedger ledger = ledger(100_000, 100_000, 0);
        assertEquals(0, ledger.acceptPackets(8, 1, R, 0, false));
    }

    @Test
    void feAndEuShareTheInputBudget() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 200, 0).withEuInput(32, 4));
        assertEquals(100, ledger.receive(100, 0, false));
        assertEquals(0, ledger.acceptPackets(32, 1, R, 0, false), "100 Q budget left < 128 Q packet");
        assertEquals(3, ledger.acceptPackets(8, 4, R, 0, false), "floor(100 Q / 32 Q) whole ULV packets");
        assertEquals(196, ledger.stored());
        assertEquals(4, ledger.receive(100, 0, false), "FE may take the last 4 Q of the shared budget");
        assertEquals(1, ledger.acceptPackets(32, 1, R, 1, false), "next tick has a fresh budget");
    }

    @Test
    void remainderStaysInTheSameBalance() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 1000, 1000).withEuOutput(1, 16));
        ledger.generate(7);
        assertEquals(1, EnergyUnits.toEu(7));
        try (EnergyLedger.Reservation packets = ledger.reservePackets(R, 0)) {
            assertEquals(1, packets.amps(), "7 Q hold one whole 1 EU packet");
            assertEquals(1, packets.commitAmps(1));
        }
        assertEquals(3, ledger.stored(), "3 Q remainder is kept once, not duplicated");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, R - 1, R, R + 1, 999, 1000})
    void feToEuConversionNeverCreatesEnergy(long feIn) {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 1000, 1000).withEuOutput(1, 1000));
        long accepted = ledger.receive(feIn, 0, false);
        long euOut;
        try (EnergyLedger.Reservation packets = ledger.reservePackets(R, 1)) {
            euOut = packets.commitAmps(packets.amps());
        }
        // Converting the EU back at the same rate must not exceed the FE that went in.
        assertEquals(accepted, euOut * R + ledger.stored());
        assertTrue(euOut * R <= accepted);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 7, 250})
    void euToFeConversionNeverCreatesEnergy(long euIn) {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1_000_000, 1_000_000, 1_000_000).withEuInput(1, 1000));
        long amps = ledger.acceptPackets(1, euIn, R, 0, false);
        assertEquals(euIn, amps);
        long feOut = 0;
        for (long tick = 1; ledger.stored() > 0; tick++) {
            feOut += ledger.extract(Integer.MAX_VALUE, tick, false);
        }
        assertEquals(euIn * R, feOut);
    }

    @Test
    void reservationBlocksReentrantDoubleSpend() {
        EnergyLedger ledger = ledger(1000, 1000, 1000);
        ledger.generate(500);
        try (EnergyLedger.Reservation reservation = ledger.reserveOutput(Integer.MAX_VALUE, 0)) {
            assertEquals(500, reservation.amount());
            // A neighbour calling back into the machine during the push finds nothing to take...
            assertEquals(0, ledger.extract(500, 0, false));
            // ...and cannot overfill it either: the reserved energy still occupies capacity.
            assertEquals(500, ledger.receive(1000, 0, false));
            assertEquals(200, reservation.commit(200));
        }
        assertEquals(800, ledger.stored(), "300 refunded + 500 received");
        assertTrue(ledger.stored() <= ledger.capacity());
    }

    @Test
    void closingAReservationRefundsBalanceAndBudget() {
        EnergyLedger ledger = ledger(1000, 0, 100);
        ledger.generate(500);
        try (EnergyLedger.Reservation reservation = ledger.reserveOutput(80, 3)) {
            assertEquals(80, reservation.amount());
        }
        assertEquals(500, ledger.stored());
        assertEquals(100, ledger.extract(1000, 3, false), "the refunded amount is back in the budget");
    }

    @Test
    void commitIsClampedToTheReservation() {
        EnergyLedger ledger = ledger(1000, 0, 1000);
        ledger.generate(100);
        try (EnergyLedger.Reservation reservation = ledger.reserveOutput(50, 0)) {
            assertEquals(50, reservation.commit(Long.MAX_VALUE), "a neighbour cannot report more than offered");
            assertEquals(0, reservation.commit(50), "settles only once");
        }
        assertEquals(50, ledger.stored());
        try (EnergyLedger.Reservation reservation = ledger.reserveOutput(50, 1)) {
            assertEquals(0, reservation.commit(-20));
        }
        assertEquals(50, ledger.stored());
    }

    @Test
    void packetReservationRespectsOutputAmpsAcrossFaces() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 0, 100_000).withEuOutput(32, 2));
        ledger.generate(100_000);
        try (EnergyLedger.Reservation first = ledger.reservePackets(R, 0)) {
            assertEquals(2, first.amps());
            first.commitAmps(1);
        }
        try (EnergyLedger.Reservation second = ledger.reservePackets(R, 0)) {
            assertEquals(1, second.amps(), "one ampere left this tick");
            second.commitAmps(5);
        }
        try (EnergyLedger.Reservation third = ledger.reservePackets(R, 0)) {
            assertEquals(0, third.amps());
        }
        assertEquals(100_000 - 2 * 128, ledger.stored());
    }

    @Test
    void internalOperationsIgnoreBudgetsButNotCapacity() {
        EnergyLedger ledger = ledger(100, 0, 0);
        assertEquals(100, ledger.generate(250));
        assertTrue(ledger.tryConsume(60));
        assertFalse(ledger.tryConsume(41), "all or nothing");
        assertEquals(40, ledger.stored());
        assertTrue(ledger.tryConsume(0));
        assertThrows(IllegalArgumentException.class, () -> ledger.tryConsume(-1));
    }

    @Test
    void loadClampsCorruptValues() {
        EnergyLedger ledger = ledger(100, 10, 10);
        assertTrue(ledger.loadStored(-5));
        assertEquals(0, ledger.stored());
        assertTrue(ledger.loadStored(Long.MAX_VALUE));
        assertEquals(100, ledger.stored());
        assertFalse(ledger.loadStored(42));
        assertEquals(42, ledger.stored());
    }

    @Test
    void shrinkingCapacityKeepsEnergy() {
        EnergyLedger ledger = ledger(1000, 1000, 1000);
        ledger.generate(900);
        ledger.setLimits(EnergyLimits.fe(500, 1000, 1000));
        assertEquals(900, ledger.stored());
        assertEquals(0, ledger.space());
        assertEquals(0, ledger.receive(10, 0, false));
        assertEquals(900, ledger.extract(1000, 0, false));
    }

    @Test
    void longBoundariesDoNotOverflow() {
        EnergyLedger ledger = new EnergyLedger(new EnergyLimits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE - 1, ledger.receive(Long.MAX_VALUE - 1, 0, false));
        assertEquals(1, ledger.receive(Long.MAX_VALUE, 1, false));
        assertEquals(0, ledger.receive(1, 2, false));
        assertEquals(0, ledger.acceptPackets(Long.MAX_VALUE, 1, R, 3, false), "overflowing packet size is rejected");
        try (EnergyLedger.Reservation packets = ledger.reservePackets(R, 4)) {
            assertEquals(0, packets.amps(), "overflowing output packet size reserves nothing");
        }
        assertEquals(Long.MAX_VALUE, ledger.stored());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnergyLimits.fe(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> EnergyLimits.fe(10, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> EnergyLimits.fe(10, 1, 1).withEuInput(-32, 1));
    }
}
