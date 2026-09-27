package theflogat.technomancy.common.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FeEnergyViewTest {

    private final AtomicLong clock = new AtomicLong();
    private final AtomicBoolean server = new AtomicBoolean(true);

    private FeEnergyView view(EnergyLedger ledger, boolean input, boolean output) {
        return new FeEnergyView(ledger, clock::get, server::get, input, output);
    }

    @Test
    void facePermissionsAreEnforced() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 100, 100));
        ledger.generate(500);
        FeEnergyView inputOnly = view(ledger, true, false);
        FeEnergyView outputOnly = view(ledger, false, true);
        FeEnergyView readOnly = view(ledger, false, false);

        assertTrue(inputOnly.canReceive());
        assertFalse(inputOnly.canExtract());
        assertEquals(0, inputOnly.extractEnergy(50, false));
        assertEquals(0, outputOnly.receiveEnergy(50, false));
        assertEquals(0, readOnly.receiveEnergy(50, false));
        assertEquals(0, readOnly.extractEnergy(50, false));
        assertEquals(500, readOnly.getEnergyStored());
        assertEquals(500, ledger.stored());
    }

    @Test
    void viewsOfOneLedgerShareOneBudget() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 100, 100));
        FeEnergyView north = view(ledger, true, false);
        FeEnergyView south = view(ledger, true, false);
        assertEquals(70, north.receiveEnergy(70, false));
        assertEquals(30, south.receiveEnergy(70, false));
        assertEquals(0, north.receiveEnergy(70, false));
        clock.incrementAndGet();
        assertEquals(70, south.receiveEnergy(70, false));
        assertEquals(170, ledger.stored());
    }

    @Test
    void clientCannotCommitButCanSimulate() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 100, 100));
        ledger.generate(200);
        FeEnergyView both = view(ledger, true, true);
        server.set(false);
        assertEquals(100, both.receiveEnergy(100, true));
        assertEquals(0, both.receiveEnergy(100, false));
        assertEquals(100, both.extractEnergy(100, true));
        assertEquals(0, both.extractEnergy(100, false));
        assertEquals(200, ledger.stored());
    }

    @Test
    void longValuesAreClampedToInt() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        ledger.generate(Long.MAX_VALUE / 2);
        FeEnergyView view = view(ledger, true, true);
        assertEquals(Integer.MAX_VALUE, view.getEnergyStored());
        assertEquals(Integer.MAX_VALUE, view.getMaxEnergyStored());
        assertEquals(Integer.MAX_VALUE, view.extractEnergy(Integer.MAX_VALUE, false));
        assertEquals(Integer.MAX_VALUE, view.receiveEnergy(Integer.MAX_VALUE, false));
    }

    @Test
    void zeroBudgetMeansCannotTransfer() {
        EnergyLedger ledger = new EnergyLedger(EnergyLimits.fe(1000, 0, 0));
        FeEnergyView view = view(ledger, true, true);
        assertFalse(view.canReceive());
        assertFalse(view.canExtract());
    }
}
