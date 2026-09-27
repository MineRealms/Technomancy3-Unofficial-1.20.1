package theflogat.technomancy.common.essentia;

/**
 * Suction a store advertises, i.e. the pressure that makes a tube hand essentia over.
 *
 * <p>TC4 advertises more suction once a container is labelled, so a labelled jar outbids an
 * unlabelled one for the aspect it wants. The same pair of numbers is also the minimum
 * suction the store demands before it will give essentia away, which is what stops two
 * equal containers from passing the same unit back and forth forever.</p>
 *
 * <p>{@code perUnit} adds one point of suction per that many stored units. TC4's own jars do
 * not scale ({@code perUnit == 0}); Technomancy's quantum jar does, which is how a nearly
 * full one outbids everything else for the aspect it is already hoarding.</p>
 */
public record EssentiaSuction(int labelled, int unlabelled, int perUnit) {

    /** TC4's warded jar: 64 labelled, 32 unlabelled, no scaling. */
    public static final EssentiaSuction JAR = new EssentiaSuction(64, 32, 0);
    /** TC4's void jar, which deliberately outbids an unlabelled jar but not a labelled one. */
    public static final EssentiaSuction VOID_JAR = new EssentiaSuction(48, 32, 0);

    public EssentiaSuction {
        if (labelled < 0 || unlabelled < 0) {
            throw new IllegalArgumentException("suction cannot be negative");
        }
        if (perUnit < 0) {
            throw new IllegalArgumentException("suction scaling divisor cannot be negative");
        }
    }

    public EssentiaSuction(int labelled, int unlabelled) {
        this(labelled, unlabelled, 0);
    }

    /**
     * Suction advertised right now.
     *
     * <p>A full store stops pulling, except a void store, which keeps pulling in order to
     * destroy what it takes — that is the whole point of the void variant. A full void store
     * drops to its unlabelled figure, exactly as TC4R's warded jar does.</p>
     *
     * @param stored current contents, which only matters when {@link #perUnit} scaling is on
     */
    public int amount(boolean labelled, boolean full, boolean voidOverflow, int stored) {
        if (full && !voidOverflow) {
            return 0;
        }
        return base(labelled && !full) + bonus(stored);
    }

    /** Convenience for the unscaled stores, where the contents make no difference. */
    public int amount(boolean labelled, boolean full, boolean voidOverflow) {
        return amount(labelled, full, voidOverflow, 0);
    }

    /**
     * The suction a taker must reach before this store will give essentia away.
     *
     * <p>Deliberately without the full-store check that {@link #amount} applies: "how much do
     * I want" and "how hard is it to take from me" are different questions, and TC4R's
     * {@code WardedJarBlockEntity} keeps them apart the same way.</p>
     */
    public int minimum(boolean labelled, int stored) {
        return base(labelled) + bonus(stored);
    }

    public int minimum(boolean labelled) {
        return minimum(labelled, 0);
    }

    private int base(boolean labelled) {
        return labelled ? this.labelled : unlabelled;
    }

    private int bonus(int stored) {
        return perUnit > 0 && stored > 0 ? stored / perUnit : 0;
    }

    /**
     * The rule that always applies: essentia moves only when the taker advertises strictly
     * more suction than the giver. The comparison must stay strict — making it {@code >=}
     * lets two equal containers trade the same unit back and forth every tick.
     *
     * @param takerSuction suction advertised by the side that wants the essentia
     * @param giverSuction suction advertised by the side that holds it
     */
    public static boolean canTake(int takerSuction, int giverSuction) {
        return takerSuction > giverSuction;
    }

    /**
     * The additional gate for a taker that has not bound an aspect yet and therefore has to
     * adopt whatever the giver is offering.
     *
     * <p>This asymmetry is TC4 behaviour, not an oversight of ours: TC4's jar checks the
     * giver's {@code minimumSuction()} only on this discovery branch, and once the aspect is
     * known — from a filter or from what is already stored — it re-checks the suction
     * comparison alone. TC4R reproduces that shape in
     * {@code WardedJarBlockEntity.fillJar} (see its lines 170-178), so a container can pull a
     * bound aspect from a giver whose minimum it does not meet. Protocol-visible behaviour
     * that other mods' tubes rely on is preserved deliberately; do not "fix" it here.</p>
     *
     * @param giverMinimum {@code minimumSuction()} of the side that holds the essentia
     */
    public static boolean canDiscover(int takerSuction, int giverSuction, int giverMinimum) {
        return canTake(takerSuction, giverSuction) && takerSuction >= giverMinimum;
    }
}
