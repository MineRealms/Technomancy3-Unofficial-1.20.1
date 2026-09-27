package theflogat.technomancy.common.energy;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Forge Energy view of one face of an {@link EnergyLedger}. It owns no balance; every call
 * is translated into a budgeted ledger operation. Values are clamped to {@code int}, while
 * the ledger itself stays {@code long}.
 */
public final class FeEnergyView implements IEnergyStorage {

    private final EnergyLedger ledger;
    private final LongSupplier clock;
    private final BooleanSupplier mutable;
    private final boolean input;
    private final boolean output;

    /**
     * @param clock   server game time, used to select the shared per-tick budget
     * @param mutable whether committing transfers is allowed right now (false on the client)
     */
    public FeEnergyView(EnergyLedger ledger, LongSupplier clock, BooleanSupplier mutable, boolean input, boolean output) {
        this.ledger = ledger;
        this.clock = clock;
        this.mutable = mutable;
        this.input = input;
        this.output = output;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!input || maxReceive <= 0 || (!simulate && !mutable.getAsBoolean())) {
            return 0;
        }
        return (int) ledger.receive(maxReceive, clock.getAsLong(), simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!output || maxExtract <= 0 || (!simulate && !mutable.getAsBoolean())) {
            return 0;
        }
        return (int) ledger.extract(maxExtract, clock.getAsLong(), simulate);
    }

    @Override
    public int getEnergyStored() {
        return clampToInt(ledger.stored());
    }

    @Override
    public int getMaxEnergyStored() {
        return clampToInt(ledger.capacity());
    }

    @Override
    public boolean canExtract() {
        return output && ledger.limits().maxExtractPerTick() > 0;
    }

    @Override
    public boolean canReceive() {
        return input && ledger.limits().maxReceivePerTick() > 0;
    }

    static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
