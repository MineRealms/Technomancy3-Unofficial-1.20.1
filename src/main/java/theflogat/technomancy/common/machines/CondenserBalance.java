package theflogat.technomancy.common.machines;

/**
 * The energy condenser's fixed numbers and the one balance rule that must never be broken:
 * making a unit of essentia has to cost more energy than burning that unit gives back.
 *
 * <p>The condenser makes {@code potentia} and the essentia dynamo burns it, so the two blocks
 * form a closed loop. If {@code condenserCostQ} ever drops to or below what the dynamo yields
 * from one unit, "condenser &rarr; tube &rarr; dynamo &rarr; condenser" is a net producer, i.e.
 * a perpetual motion machine. The 1.7.10 pair was safe by accident (1,000,000 against 64,000,
 * a 15.6x loss); nothing in the code said so, which is why {@link #verdict} exists.</p>
 *
 * <p>The dynamo is not implemented yet, so the yield is computed from the two numbers the
 * behaviour spec pins down for it rather than from its class. Both are documented below with
 * their 1.7.10 source; if the dynamo lands with different ones, this file has to follow, and
 * {@code CondenserBalanceTest} is what will notice.</p>
 */
public final class CondenserBalance {

    /** {@code TileCondenser.aspect = Aspect.ENERGY}: the output aspect is fixed, not selectable. */
    public static final String ASPECT = "potentia";

    /** {@code TileCondenser.maxAmount}: the internal essentia buffer, in units. */
    public static final int ESSENTIA_CAPACITY = 64;

    /**
     * Energy buffer in Q.
     *
     * <p>Deliberate deviation: the original sized it at {@code cost * 5} (5,000,000 Q) because
     * it could only spend energy in one whole {@code cost} lump and therefore had to hoard.
     * With the steady progress model of {@link CondenserProduction} the buffer only has to
     * cover a few ticks of draw, so it is a flat 100,000 Q.</p>
     */
    public static final long ENERGY_CAPACITY_Q = 100_000;

    /**
     * Maximum energy the condenser converts per tick, in Q.
     *
     * <p>The original had no rate limit at all: it waited until a whole {@code cost} had
     * accumulated and then took the lot in a single tick, so a small power source showed
     * nothing for minutes and then jumped by one unit. This is the same total cost spread
     * evenly, which is also what makes a progress readout possible.</p>
     */
    public static final long MAX_RATE_Q_PER_TICK = 8_192;

    /**
     * {@code TileEssentiaDynamo.getAspectFuel} returns 800 for {@code Aspect.ENERGY}
     * (1.7.10 {@code TileEssentiaDynamo.java:62-64}).
     */
    public static final long POTENTIA_FUEL_VALUE = 800;

    /**
     * Q one point of dynamo fuel becomes: the dynamo burns one fuel point per tick while
     * producing 80 RF/t (1.7.10 {@code TileDynamoBase.java:42}), and 1 RF maps to 1 Q.
     */
    public static final long Q_PER_FUEL_POINT = 80;

    /** Below this multiple of the dynamo yield the loop is legal but arguably too generous. */
    private static final long COMFORTABLE_MARGIN = 4;

    /** Outcome of {@link #verdict}, worst first. */
    public enum Verdict {
        /** The loop produces at least as much as it costs: the pair is a free energy source. */
        PERPETUAL_MOTION,
        /** Legal, but the recovery rate is high enough that the loop is worth building. */
        NARROW,
        /** The intended shape: the condenser is an energy sink, not a step in a power chain. */
        SAFE
    }

    private CondenserBalance() {
    }

    /**
     * Energy a dynamo gives back for one unit of the aspect the condenser makes.
     *
     * @param fuelScale {@code balance.essentiaFuelScale}; shortens the burn, so it scales the
     *                  total energy of a unit without touching the dynamo's output rate
     */
    public static long dynamoYieldPerUnitQ(double fuelScale) {
        if (!(fuelScale > 0)) {
            return 0;
        }
        double yield = POTENTIA_FUEL_VALUE * (double) Q_PER_FUEL_POINT * fuelScale;
        // Floor, because the dynamo cannot hand back a fraction of a Q either.
        return yield >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(yield);
    }

    /** Classifies a condenser cost against the dynamo yield it has to beat. */
    public static Verdict verdict(long condenserCostQ, double fuelScale) {
        long yield = dynamoYieldPerUnitQ(fuelScale);
        if (condenserCostQ <= yield) {
            return Verdict.PERPETUAL_MOTION;
        }
        // Guard the multiplication: a huge fuel scale must not wrap into a "safe" answer.
        long comfortable = yield > Long.MAX_VALUE / COMFORTABLE_MARGIN
                ? Long.MAX_VALUE : yield * COMFORTABLE_MARGIN;
        return condenserCostQ <= comfortable ? Verdict.NARROW : Verdict.SAFE;
    }

    /** One line explaining a verdict, including the value the config would need. */
    public static String describe(long condenserCostQ, double fuelScale) {
        long yield = dynamoYieldPerUnitQ(fuelScale);
        Verdict verdict = verdict(condenserCostQ, fuelScale);
        String head = "balance.condenserCostQ=" + condenserCostQ + " against a dynamo yield of " + yield
                + " Q per unit of " + ASPECT + " at balance.essentiaFuelScale=" + fuelScale;
        return switch (verdict) {
            case PERPETUAL_MOTION -> head + ": a condenser feeding a dynamo now returns at least what it"
                    + " spends, which is free energy. Raise condenserCostQ above " + yield
                    + " or lower essentiaFuelScale.";
            case NARROW -> head + ": legal, but the loop recovers more than a quarter of its cost."
                    + " Above " + saturatedTimes(yield, COMFORTABLE_MARGIN) + " is the recommended range.";
            case SAFE -> head + ": the condenser is a net energy sink, as intended.";
        };
    }

    private static long saturatedTimes(long value, long factor) {
        return value > Long.MAX_VALUE / factor ? Long.MAX_VALUE : value * factor;
    }
}
