package theflogat.technomancy.compat.kubejs;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.essentia.fuel.FuelEnvironment;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The {@code Technom} global a KubeJS script sees.
 *
 * <p>Only the numbers are exposed, and only the ones a script cannot get at another way. Every
 * recipe this mod adds is an ordinary data-pack recipe that a pack can already override, so
 * there is no recipe schema and no builder here; what a pack <em>cannot</em> read is the
 * essentia fuel table, which lives behind a codec in a datapack and is never shown as an item.
 * That, the Q/EU rate and the fuel scale are what this class answers.</p>
 *
 * <p>Static methods on a bound class, so a script writes {@code Technom.maxFuelValue("ignis")}.
 * KubeJS binds its own helpers the same way.</p>
 */
public final class TechnomJS {

    /** Q one fuel point is worth before {@code essentiaFuelScale}. The 1.7.10 dynamo's 80 RF. */
    public static final long Q_PER_FUEL_POINT = EssentiaFuelTable.Q_PER_FUEL_POINT;

    private TechnomJS() {
    }

    /** Q per GT EU in force this session; frozen at startup from the config. */
    public static long qPerEu() {
        return EnergyUnits.qPerEu();
    }

    /** Multiplier on what one unit of essentia yields in a dynamo. */
    public static double essentiaFuelScale() {
        return TechnomancyConfig.ESSENTIA_FUEL_SCALE.get();
    }

    /** The aspects the loaded table names, in its own order. */
    public static List<String> listedAspects() {
        List<String> ids = new ArrayList<>();
        for (AspectId aspect : EssentiaFuelLoader.table().listedAspects()) {
            ids.add(aspect.serialized());
        }
        return ids;
    }

    /** What an aspect with no row of its own is worth. */
    public static int fallbackFuelValue() {
        return EssentiaFuelLoader.table().fallback();
    }

    /**
     * The most one unit of {@code aspect} can be worth in any biome, dimension or height band.
     *
     * <p>The worst case, not the value where the script happens to be. A balance check has to use
     * this one: a loop only has to be profitable in a single biome to be worth building.</p>
     *
     * @throws IllegalArgumentException if {@code aspect} is not a valid aspect id
     */
    public static int maxFuelValue(String aspect) {
        return EssentiaFuelLoader.table().maxFuelValue(require(aspect));
    }

    /** Q one unit of {@code aspect} yields at best, at the configured fuel scale. */
    public static long maxEnergyPerUnit(String aspect) {
        return EssentiaFuelTable.energyPerUnit(maxFuelValue(aspect), essentiaFuelScale());
    }

    /**
     * What one unit of {@code aspect} is worth at {@code pos} right now, conditions included.
     *
     * @throws IllegalArgumentException if {@code aspect} is not a valid aspect id
     */
    public static int fuelValueAt(Level level, BlockPos pos, String aspect) {
        return EssentiaFuelLoader.table().fuelValue(FuelEnvironment.of(level, pos), require(aspect));
    }

    /**
     * Rejects an unknown aspect rather than answering the fallback.
     *
     * <p>{@code fuelValue} would happily return the fallback for any id at all, so a script that
     * mistyped {@code "ignus"} would read 25 and never learn why. The registry is the only thing
     * that can tell the two apart.</p>
     */
    private static AspectId require(String aspect) {
        AspectId id = AspectId.parse(aspect);
        if (!AspectApi.registry().get(id).isPresent()) {
            throw new IllegalArgumentException("Unknown aspect: " + aspect);
        }
        return id;
    }
}
