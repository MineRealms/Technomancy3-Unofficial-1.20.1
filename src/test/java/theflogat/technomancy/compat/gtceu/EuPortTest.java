package theflogat.technomancy.compat.gtceu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.energy.EnergyLedger;
import theflogat.technomancy.common.energy.EnergyLimits;

class EuPortTest {

    private static final long R = 4; // Q per EU used throughout
    private static final Direction FACE = Direction.NORTH;

    private final AtomicLong clock = new AtomicLong();
    private final AtomicBoolean server = new AtomicBoolean(true);

    private EuPort port(EnergyLedger ledger, @Nullable Direction face, boolean input, boolean output) {
        return new EuPort(ledger, clock::get, server::get, R, face, input, output);
    }

    private static EnergyLedger consumer() {
        return new EnergyLedger(EnergyLimits.fe(100_000, 100_000, 0).withEuInput(32, 2));
    }

    private static EnergyLedger generator() {
        return new EnergyLedger(EnergyLimits.fe(100_000, 0, 100_000).withEuOutput(128, 3));
    }

    @Test
    void acceptsWholePacketsAndReturnsAmperes() {
        EnergyLedger ledger = consumer();
        EuPort port = port(ledger, FACE, true, false);
        assertTrue(port.inputs(FACE));
        assertEquals(32, port.inputVoltage());
        assertEquals(2, port.inputAmperage());

        assertEquals(2, port.acceptAmps(FACE, 32, 5), "bounded by the rated ampere limit");
        assertEquals(2 * 32 * R, ledger.stored());
        assertEquals(2 * 32, port.storedEu());
        assertEquals(0, port.acceptAmps(FACE, 32, 1), "no amperes left this tick");
        clock.incrementAndGet();
        assertEquals(1, port.acceptAmps(FACE, 32, 1), "next tick has a fresh quota");
    }

    @Test
    void overVoltageIsRefusedOutright() {
        EnergyLedger ledger = consumer();
        EuPort port = port(ledger, FACE, true, false);
        assertEquals(0, port.acceptAmps(FACE, 33, 1));
        assertEquals(0, port.acceptAmps(FACE, 512, 4));
        assertEquals(0, ledger.stored(), "an over-voltage packet is not truncated, it is dropped");
        assertEquals(1, port.acceptAmps(FACE, 31, 1), "a lossy cable below the rating still works");
        assertEquals(31 * R, ledger.stored());
    }

    @Test
    void remainderBelowOneEuStaysInTheLedger() {
        EnergyLedger ledger = consumer();
        EuPort port = port(ledger, FACE, true, false);
        ledger.generate(7);
        assertEquals(1, port.storedEu(), "7 Q is one whole EU at r=4");
        assertEquals(7, ledger.stored(), "reading EU never rounds the balance");
        assertEquals(100_000 / R, port.capacityEu());
        assertEquals((100_000 - 7) / R, port.insertableEu());
    }

    @Test
    void aNullSidePortHasNoTransferRights() {
        EnergyLedger ledger = consumer();
        ledger.generate(1000);
        // This is how the protocol builds the unsided view: readable, but never a transfer path.
        EuPort probe = port(ledger, null, false, false);
        assertFalse(probe.inputs(null));
        assertFalse(probe.inputs(FACE));
        assertFalse(probe.outputs(null));
        assertEquals(0, probe.inputVoltage());
        assertEquals(0, probe.inputAmperage());
        assertEquals(0, probe.outputVoltage());
        assertEquals(0, probe.outputAmperage());
        assertEquals(0, probe.acceptAmps(null, 32, 1));
        assertEquals(0, probe.acceptAmps(FACE, 32, 1), "naming a face cannot grant rights either");
        assertEquals(1000, ledger.stored());
        assertEquals(250, probe.storedEu(), "reading stays allowed");
    }

    @Test
    void aFaceBoundPortServesItsOwnFaceAndUnsidedCalls() {
        EnergyLedger ledger = consumer();
        EuPort port = port(ledger, FACE, true, false);
        assertTrue(port.inputs(FACE));
        assertTrue(port.inputs(null), "GT passes null for 'no side restriction'");
        assertFalse(port.inputs(FACE.getOpposite()));
        assertEquals(0, port.acceptAmps(Direction.SOUTH, 32, 1), "another face must not use this view");
        assertEquals(1, port.acceptAmps(null, 32, 1));
        assertEquals(32 * R, ledger.stored());
    }

    @Test
    void facePermissionsAreEnforcedIndependently() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 100_000, 100_000)
                .withEuInput(32, 2).withEuOutput(128, 3));
        EuPort inputOnly = port(ledger, FACE, true, false);
        EuPort outputOnly = port(ledger, FACE, false, true);
        EuPort readOnly = port(ledger, FACE, false, false);

        assertTrue(inputOnly.inputs(FACE));
        assertFalse(inputOnly.outputs(FACE));
        assertEquals(0, inputOnly.outputVoltage());
        assertEquals(0, inputOnly.outputAmperage());

        assertTrue(outputOnly.outputs(FACE));
        assertEquals(128, outputOnly.outputVoltage());
        assertEquals(3, outputOnly.outputAmperage());
        assertEquals(0, outputOnly.acceptAmps(FACE, 32, 1));

        assertFalse(readOnly.inputs(FACE));
        assertFalse(readOnly.outputs(FACE));
        assertEquals(0, readOnly.acceptAmps(FACE, 32, 1));
        assertEquals(0, ledger.stored());
    }

    @Test
    void limitsWithoutEuDisableThePort() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 100_000, 100_000));
        EuPort port = port(ledger, FACE, true, true);
        assertFalse(port.inputs(FACE), "the machine has no EU rating at all");
        assertFalse(port.outputs(FACE));
        assertEquals(0, port.inputVoltage());
        assertEquals(0, port.outputVoltage());
        assertEquals(0, port.acceptAmps(FACE, 8, 1));
    }

    @Test
    void theClientCanReadButNeverCommit() {
        EnergyLedger ledger = consumer();
        EuPort port = port(ledger, FACE, true, false);
        server.set(false);
        assertEquals(0, port.acceptAmps(FACE, 32, 1));
        assertEquals(0, ledger.stored());
        assertEquals(100_000 / R, port.capacityEu());
    }

    @Test
    void inputSharesTheLedgerBudgetWithFe() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(100_000, 200, 0).withEuInput(32, 4));
        EuPort port = port(ledger, FACE, true, false);
        assertEquals(100, ledger.receive(100, 0, false));
        assertEquals(0, port.acceptAmps(FACE, 32, 1), "100 Q of budget left is less than one 128 Q packet");
        assertEquals(3, port.acceptAmps(FACE, 8, 4), "three whole 32 Q packets fit into the rest");
        assertEquals(196, ledger.stored());
    }

    @Test
    void outputReadingsMatchTheReservationVoltage() {
        EnergyLedger ledger = generator();
        ledger.generate(100_000);
        EuPort port = port(ledger, FACE, false, true);
        try (EnergyLedger.Reservation packets = ledger.reservePackets(R, 0)) {
            assertEquals(port.outputVoltage() * R, packets.qPerAmp(),
                    "an advertised voltage that does not match the offer would misprice every push");
            assertEquals(port.outputAmperage(), packets.amps());
        }
    }

    @Test
    void aNonPositiveRateIsRejected() {
        EnergyLedger ledger = consumer();
        assertThrows(IllegalArgumentException.class,
                () -> new EuPort(ledger, clock::get, server::get, 0, FACE, true, false));
    }
}
