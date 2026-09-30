package theflogat.technomancy.common.essentia.fuel;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * A resolved aspect-to-fuel-value table.
 *
 * <p>Immutable: a data-pack reload publishes a new instance rather than mutating a live one, so
 * a dynamo mid-tick can never see half a table.</p>
 */
public final class EssentiaFuelTable {

    /**
     * Energy one fuel point is worth at the base generation rate, before
     * {@link TechnomancyConfig#ESSENTIA_FUEL_SCALE}. The 1.7.10 dynamo produced 80 RF per fuel
     * tick and one fuel point was one tick, so this is the 1:1 mapping of that number into Q.
     */
    public static final long Q_PER_FUEL_POINT = 80;

    /** Nothing burns; the state before the first data-pack load. */
    public static final EssentiaFuelTable EMPTY = new EssentiaFuelTable(Map.of(), 0);

    private final Map<AspectId, EssentiaFuelEntry> byAspect;
    private final int fallback;

    private EssentiaFuelTable(Map<AspectId, EssentiaFuelEntry> byAspect, int fallback) {
        this.byAspect = byAspect;
        this.fallback = fallback;
    }

    /**
     * Builds a table. A later entry naming an aspect replaces an earlier one, which is how a
     * data pack overrides a single row without restating the whole table.
     */
    public static EssentiaFuelTable of(Collection<EssentiaFuelEntry> entries, int fallback) {
        if (fallback < 0) {
            throw new IllegalArgumentException("fallback fuel value must not be negative: " + fallback);
        }
        Map<AspectId, EssentiaFuelEntry> byAspect = new LinkedHashMap<>();
        for (EssentiaFuelEntry entry : entries) {
            for (AspectId aspect : entry.aspects()) {
                byAspect.put(aspect, entry);
            }
        }
        return new EssentiaFuelTable(Collections.unmodifiableMap(byAspect), fallback);
    }

    /** Value used for any aspect with no row of its own. */
    public int fallback() {
        return fallback;
    }

    public Set<AspectId> listedAspects() {
        return byAspect.keySet();
    }

    /**
     * The row covering {@code aspect}, or {@code null} when it falls through to {@link #fallback()}.
     *
     * <p>Exposed so a display can show the row's conditions and random bonus, which
     * {@link #fuelValue} and {@link #maxFuelValue} deliberately collapse into one number.</p>
     */
    @Nullable
    public EssentiaFuelEntry entryFor(AspectId aspect) {
        return byAspect.get(aspect);
    }

    public boolean isEmpty() {
        return byAspect.isEmpty() && fallback == 0;
    }

    /**
     * Fuel value of one unit of {@code aspect} here and now, or 0 for no aspect.
     *
     * <p>An aspect with no row of its own gets {@link #fallback}, matching the original's
     * {@code return 25} at the end of the chain. That is why the fallback is data too: it is
     * the value of every aspect the table does not mention, including ones added by other mods.</p>
     */
    public int fuelValue(FuelEnvironment environment, @Nullable AspectId aspect) {
        if (aspect == null) {
            return 0;
        }
        EssentiaFuelEntry entry = byAspect.get(aspect);
        return entry == null ? fallback : entry.resolve(environment);
    }

    /**
     * The most one unit of {@code aspect} can be worth anywhere.
     *
     * <p>A balance check has to use this rather than the value in any one place, because a loop
     * only needs to be profitable in a single biome, dimension or height band to be exploitable.</p>
     */
    public int maxFuelValue(AspectId aspect) {
        EssentiaFuelEntry entry = byAspect.get(aspect);
        return entry == null ? fallback : entry.maxValue();
    }

    /**
     * Energy one unit of {@code aspect} is worth, in Q, at the configured fuel scale.
     *
     * <p>{@code fuelValue x 80 x essentiaFuelScale}. This figure is per unit of essentia and is
     * therefore independent of the potency gem: the gem quadruples both the generation rate and
     * the units consumed, so it is pure throughput and changes no efficiency
     * (see {@code en_US.lang:110}, "four times the power ... four times the fuel").</p>
     */
    public long energyPerUnit(FuelEnvironment environment, @Nullable AspectId aspect) {
        return energyPerUnit(environment, aspect, TechnomancyConfig.ESSENTIA_FUEL_SCALE.get());
    }

    /** Same, at an explicit scale; the entry point that does not need a loaded config. */
    public long energyPerUnit(FuelEnvironment environment, @Nullable AspectId aspect, double scale) {
        return energyPerUnit(fuelValue(environment, aspect), scale);
    }

    /**
     * Q per unit of essentia for an already-resolved fuel value.
     *
     * <p>Rounded once, here, rather than per burn step: the dynamo banks a charge's remaining
     * energy in Q and spends it at its rate, so the total a unit yields is exactly this number
     * however long it takes to come out. Scaling the fuel value instead of the rate is what
     * keeps the machine feeling identical while its running costs change (spec 3.7).</p>
     */
    public static long energyPerUnit(int fuelValue, double scale) {
        if (fuelValue <= 0 || scale <= 0) {
            return 0;
        }
        return Math.round(fuelValue * (double) Q_PER_FUEL_POINT * scale);
    }
}
