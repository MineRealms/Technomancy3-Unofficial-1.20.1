package theflogat.technomancy.common.tiles.dynamo;

import java.util.function.LongSupplier;

/**
 * The fuel bank of a dynamo: energy that has already been paid for in essentia and not yet
 * produced, plus the rules for topping it up and spending it.
 *
 * <p>Fuel is held as energy in Q rather than as a count of burn ticks. The 1.7.10 dynamo banked
 * ticks, which only worked because one tick was always exactly 80 RF; once the value of a unit
 * of essentia is scaled by configuration, a tick count either rounds or has to carry a
 * fractional remainder. Banking energy instead makes what a unit of essentia is worth exact and
 * identical with and without the potency gem, because the gem multiplies the rate and the units
 * per charge by the same four.</p>
 *
 * <p>Deliberately free of any world, ledger or essentia store, so the ordering hazard it encodes
 * - buy fuel before burning it, and do neither when the buffer is full - is directly testable.</p>
 */
public final class DynamoFuelBank {

    /** Nothing bought, nothing burned. */
    public static final Burn NOTHING = new Burn(0, 0);

    private final int lookaheadTicks;
    private long banked;

    /**
     * @param lookaheadTicks how many ticks of output may be banked before buying more; the
     *                       original's {@code if (fuel < 32)}
     */
    public DynamoFuelBank(int lookaheadTicks) {
        if (lookaheadTicks <= 0) {
            throw new IllegalArgumentException("lookahead must be positive: " + lookaheadTicks);
        }
        this.lookaheadTicks = lookaheadTicks;
    }

    /** Energy the bank can still produce, in Q. */
    public long banked() {
        return banked;
    }

    /** Restores a saved bank, clamping a corrupt negative value to zero. */
    public void load(long saved) {
        banked = Math.max(0, saved);
    }

    /** One tick of work. */
    public record Burn(int unitsConsumed, long energyProduced) {}

    /**
     * Buys at most one charge and then burns at most one tick of the bank.
     *
     * <p>A full buffer ({@code space <= 0}) does neither, and {@code energyPerUnit} is not even
     * consulted. The original called {@code extractFuel(calcEner())} with a clamped
     * {@code calcEner()} of 0, so {@code ceil(0) == 0} units were charged while a whole fuel
     * value was banked, and the bank was then spent one tick at a time producing nothing: a
     * free-fuel path and a fuel-loss path in the same place, picked by timing (defect
     * A-14).</p>
     *
     * <p>A charge is all or nothing: a boosted dynamo with three units of essentia burns none
     * of them, matching {@code if (ratio > amount) return 0}.</p>
     *
     * @param ratePerTick    Q produced per tick at the current upgrade level
     * @param unitsPerCharge units of essentia one charge costs
     * @param available      units of the burnable aspect on hand
     * @param energyPerUnit  Q one unit is worth here and now; only called if a charge is bought
     * @param space          free room in the energy buffer, in Q
     * @return the units to deduct from the essentia store and the Q to hand to the ledger
     */
    public Burn tick(long ratePerTick, int unitsPerCharge, int available, LongSupplier energyPerUnit,
            long space) {
        if (space <= 0 || ratePerTick <= 0) {
            return NOTHING;
        }
        int units = 0;
        if (banked < lookaheadTicks * ratePerTick && unitsPerCharge > 0 && available >= unitsPerCharge) {
            long perUnit = energyPerUnit.getAsLong();
            if (perUnit > 0) {
                // Worth nothing here and now means charging nothing: the original's Permutatio
                // roll of 0 took a unit of essentia and returned no fuel at all (defect A-16).
                units = unitsPerCharge;
                banked += units * perUnit;
            }
        }
        long produced = Math.min(Math.min(ratePerTick, banked), space);
        banked -= produced;
        return new Burn(units, produced);
    }

    /** Returns energy the ledger refused, so a rejected transfer can never destroy fuel. */
    public void refund(long q) {
        if (q > 0) {
            banked += q;
        }
    }
}
