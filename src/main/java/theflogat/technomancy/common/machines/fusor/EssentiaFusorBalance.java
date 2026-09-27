package theflogat.technomancy.common.machines.fusor;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import javax.annotation.Nullable;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;

/**
 * What one fusion costs, and why it costs that.
 *
 * <p>The 1.7.10 fusor charged a flat {@code Rate.fusorCost = 1000} RF ({@code TileEssentiaFusor}
 * :35/:73-77). That number cannot simply be carried over, because this port also has an essentia
 * dynamo with a data-driven fuel table: a compound aspect can be worth far more as fuel than the
 * two components it is made of, so a flat cost turns "two cheap primals plus a little energy into
 * one expensive compound" into a renewable energy profit. With the shipped table the worst case is
 * {@code permutatio} — {@code perditio} and {@code ordo} are 75 fuel points each while permutatio
 * is 200 plus a random bonus of up to 1000 — which at the default scale means about 3000 Q in for
 * up to 23980 Q out.</p>
 *
 * <p>So the cost is the <em>most a dynamo could ever pay back for the unit being made</em>:</p>
 *
 * <pre>cost = max(1000, maxFuelValue(output) x 80 x essentiaFuelScale)</pre>
 *
 * <p>Since a fusion produces exactly one unit, the energy it can unlock downstream is at most the
 * value of that unit, and the fusion already cost at least that much: the loop can never net
 * energy, at any fuel scale, in any biome, at any height. Essentia cannot be netted either,
 * because {@link FusorSides#fuse()} consumes two units for one. The floor keeps the original's
 * number for the aspects where it is already sufficient.</p>
 */
public final class EssentiaFusorBalance {

    /** {@code Rate.fusorCost}; 1 Q = 1 FE, so this is the original's figure unchanged. */
    public static final long LEGACY_COST_Q = 1000;

    /**
     * Buffer size. It has to hold one fusion at any configurable fuel scale: the worst case is
     * {@code (200 + 999) x 80 x 16 = 1534720} Q. At the default scale of 0.25 this is about 80
     * fusions, a little over the original's ten.
     */
    public static final long ENERGY_CAPACITY_Q = 2_000_000;

    private EssentiaFusorBalance() {
    }

    /** Energy one fusion into {@code output} costs, in Q. */
    public static long costQ(EssentiaFuelTable table, double scale, @Nullable AspectId output) {
        if (output == null) {
            return LEGACY_COST_Q;
        }
        return Math.max(LEGACY_COST_Q, EssentiaFuelTable.energyPerUnit(table.maxFuelValue(output), scale));
    }

    /**
     * The highest cost any fusion could have with this table, which is what
     * {@link #ENERGY_CAPACITY_Q} has to be able to hold.
     */
    public static long worstCaseCostQ(EssentiaFuelTable table, double scale) {
        long worst = costQ(table, scale, null);
        for (AspectId aspect : table.listedAspects()) {
            worst = Math.max(worst, costQ(table, scale, aspect));
        }
        return Math.max(worst, EssentiaFuelTable.energyPerUnit(table.fallback(), scale));
    }
}
