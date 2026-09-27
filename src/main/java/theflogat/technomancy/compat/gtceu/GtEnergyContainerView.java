package theflogat.technomancy.compat.gtceu;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * GTCEu {@code IEnergyContainer} view of one machine face. It is deliberately nothing but a
 * translation layer: every answer comes from {@link EuPort}, which reads the machine's single
 * energy ledger, so there is no second balance for GT to charge and discharge.
 *
 * <p>This is the only class in the integration that mentions a GT type in its signatures, which
 * keeps the testable logic reachable from plain unit tests.</p>
 */
final class GtEnergyContainerView implements IEnergyContainer {

    private final EuPort port;

    GtEnergyContainerView(EuPort port) {
        this.port = port;
    }

    @Override
    public long acceptEnergyFromNetwork(@Nullable Direction side, long voltage, long amperage) {
        return port.acceptAmps(side, voltage, amperage);
    }

    @Override
    public boolean inputsEnergy(@Nullable Direction side) {
        return port.inputs(side);
    }

    @Override
    public boolean outputsEnergy(@Nullable Direction side) {
        return port.outputs(side);
    }

    /**
     * GT documents {@code changeEnergy} as an internal bookkeeping call that bypasses voltage,
     * ampere and per-tick limits. Honouring it from outside would turn it into an unmetered
     * entry point into our ledger, so this view refuses every such change and reports that
     * nothing moved. Inter-block transfer belongs in
     * {@link #acceptEnergyFromNetwork(Direction, long, long)}, as GT's own javadoc demands.
     */
    @Override
    public long changeEnergy(long differenceAmount) {
        return 0;
    }

    @Override
    public long getEnergyStored() {
        return port.storedEu();
    }

    @Override
    public long getEnergyCapacity() {
        return port.capacityEu();
    }

    @Override
    public long getEnergyCanBeInserted() {
        return port.insertableEu();
    }

    @Override
    public long getInputVoltage() {
        return port.inputVoltage();
    }

    @Override
    public long getInputAmperage() {
        return port.inputAmperage();
    }

    @Override
    public long getOutputVoltage() {
        return port.outputVoltage();
    }

    @Override
    public long getOutputAmperage() {
        return port.outputAmperage();
    }
}
