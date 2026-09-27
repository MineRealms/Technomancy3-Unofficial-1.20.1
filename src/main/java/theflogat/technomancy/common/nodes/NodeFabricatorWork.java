package theflogat.technomancy.common.nodes;

/**
 * What one node fabricator tick may do to the node between the pair, and what it costs.
 *
 * <p>World-free so the two operations and their exact prices are testable:
 * {@code TileNodeGenerator.updateEntity} lines 86-101 charged 1000 RF plus one unit of essentia
 * to put one Vis back into an aspect the node already lists, or - with a potency gem - 10000 RF
 * plus ten units to raise that aspect's <em>base</em> by one, which is also how an aspect the node
 * never had is introduced.</p>
 */
public final class NodeFabricatorWork {

    /** {@code getEnergyStored() >= 1000} and {@code extractEnergy(1000, false)}, 1 RF -> 1 Q. */
    public static final long RECHARGE_ENERGY = 1000;
    /** {@code boost && getEnergyStored() >= 10000}. */
    public static final long EXPAND_ENERGY = 10_000;
    public static final int RECHARGE_ESSENTIA = 1;
    /** {@code amount >= 10} and {@code takeFromContainer(aspect, 10)}. */
    public static final int EXPAND_ESSENTIA = 10;
    /**
     * {@code node.getNodeVisBase(aspect) < Short.MAX_VALUE}. TC4R stores node Vis as {@code int},
     * but the cap is kept: it is a balance rule, not a storage limit.
     */
    public static final int MAX_BASE_VIS = Short.MAX_VALUE;

    private NodeFabricatorWork() {
    }

    public enum Operation {
        /** Nothing is affordable or nothing needs doing. */
        NONE,
        /** One Vis back into an existing aspect, up to its base. */
        RECHARGE,
        /** One more point of base Vis, and one Vis to go with it. */
        EXPAND
    }

    /** The chosen operation with the price it has to be paid at, in Q and units of essentia. */
    public record Plan(Operation operation, long energy, int essentia) {

        public static final Plan NOTHING = new Plan(Operation.NONE, 0, 0);

        public boolean works() {
            return operation != Operation.NONE;
        }
    }

    /**
     * @param boost      whether a potency gem is installed
     * @param energy     Q in the machine's buffer
     * @param essentia   units of the held aspect
     * @param listed     whether the node already lists this aspect, even at zero
     * @param currentVis the node's live Vis for this aspect
     * @param baseVis    the node's base Vis for this aspect
     */
    public static Plan plan(boolean boost, long energy, int essentia, boolean listed, int currentVis, int baseVis) {
        // Recharge first, exactly as the original's if/else-if ordering: a gem never stops a
        // fabricator from doing the cheap job it could already do.
        if (essentia >= RECHARGE_ESSENTIA && energy >= RECHARGE_ENERGY && listed && currentVis < baseVis) {
            return new Plan(Operation.RECHARGE, RECHARGE_ENERGY, RECHARGE_ESSENTIA);
        }
        if (boost && essentia >= EXPAND_ESSENTIA && energy >= EXPAND_ENERGY && baseVis < MAX_BASE_VIS) {
            return new Plan(Operation.EXPAND, EXPAND_ENERGY, EXPAND_ESSENTIA);
        }
        return Plan.NOTHING;
    }
}
