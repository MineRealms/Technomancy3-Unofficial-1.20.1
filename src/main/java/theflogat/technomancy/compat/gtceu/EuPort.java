package theflogat.technomancy.compat.gtceu;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import theflogat.technomancy.common.energy.EnergyLedger;
import theflogat.technomancy.common.energy.EnergyLimits;

/**
 * The EU protocol behaviour of one machine face, expressed without any {@code com.gregtechceu}
 * type so it can be unit tested while the GT API is {@code compileOnly}.
 * {@link GtEnergyContainerView} is the thin {@code IEnergyContainer} shell around it.
 *
 * <p>Like {@code FeEnergyView} this owns no balance: every number is derived from the single
 * {@link EnergyLedger} at the session's frozen Q-per-EU rate, and every transfer goes through
 * the ledger's shared per-tick budgets. EU readings are floored, so Q worth less than one EU
 * stays in the ledger instead of being rounded into existence.</p>
 */
public final class EuPort {

    private final EnergyLedger ledger;
    private final LongSupplier clock;
    private final BooleanSupplier mutable;
    private final long qPerEu;
    @Nullable
    private final Direction face;
    private final boolean input;
    private final boolean output;

    /**
     * @param clock   server game time, used to select the shared per-tick budget
     * @param mutable whether committing transfers is allowed right now (false on the client)
     * @param qPerEu  the session's frozen rate; captured here so every reading of this port
     *                agrees with the reservation sizes used while pushing
     * @param face    the face this port was handed out for, or {@code null} for an unsided query
     */
    public EuPort(EnergyLedger ledger, LongSupplier clock, BooleanSupplier mutable, long qPerEu,
            @Nullable Direction face, boolean input, boolean output) {
        if (qPerEu <= 0) {
            throw new IllegalArgumentException("Q per EU must be positive: " + qPerEu);
        }
        this.ledger = ledger;
        this.clock = clock;
        this.mutable = mutable;
        this.qPerEu = qPerEu;
        this.face = face;
        this.input = input;
        this.output = output;
    }

    /**
     * GT passes {@code null} to mean "unsided, no side restriction", which a face-bound port may
     * honour because the caller already proved it holds that face. A port handed out for a
     * {@code null} side has no rights at all, so it can never gain them back through this rule.
     */
    private boolean serves(@Nullable Direction side) {
        return side == null || side == face;
    }

    private boolean inputEnabled() {
        return input && ledger.limits().acceptsEu();
    }

    private boolean outputEnabled() {
        return output && ledger.limits().emitsEu();
    }

    public boolean inputs(@Nullable Direction side) {
        return inputEnabled() && serves(side);
    }

    public boolean outputs(@Nullable Direction side) {
        return outputEnabled() && serves(side);
    }

    /**
     * Receives whole {@code voltage × qPerEu} packets and returns the number of accepted
     * amperes. A refused packet yields 0 and changes nothing; in particular an over-voltage
     * packet is rejected entirely rather than truncated to the rated voltage.
     */
    public long acceptAmps(@Nullable Direction side, long voltage, long amperage) {
        if (!inputs(side) || !mutable.getAsBoolean()) {
            return 0;
        }
        return ledger.acceptPackets(voltage, amperage, qPerEu, clock.getAsLong(), false);
    }

    public long storedEu() {
        return ledger.stored() / qPerEu;
    }

    public long capacityEu() {
        return ledger.capacity() / qPerEu;
    }

    /**
     * Free capacity in whole EU. GT senders skip a container that reports 0 here, which is the
     * wanted answer: with less than one EU of room no packet of any voltage can fit. The per-tick
     * input budget is not folded in, so a sender may still call and be told 0 amperes.
     */
    public long insertableEu() {
        return ledger.space() / qPerEu;
    }

    public long inputVoltage() {
        return inputEnabled() ? limits().euInputVoltage() : 0;
    }

    public long inputAmperage() {
        return inputEnabled() ? limits().euInputAmps() : 0;
    }

    public long outputVoltage() {
        return outputEnabled() ? limits().euOutputVoltage() : 0;
    }

    public long outputAmperage() {
        return outputEnabled() ? limits().euOutputAmps() : 0;
    }

    private EnergyLimits limits() {
        return ledger.limits();
    }
}
