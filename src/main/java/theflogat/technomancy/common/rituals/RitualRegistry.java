package theflogat.technomancy.common.rituals;

import java.util.ArrayList;
import java.util.List;
import theflogat.technomancy.common.rituals.light.RitualPurificationT1;
import theflogat.technomancy.common.rituals.light.RitualPurificationT2;
import theflogat.technomancy.common.rituals.light.RitualPurificationT3;
import theflogat.technomancy.common.rituals.water.RitualWaterT1;
import theflogat.technomancy.common.rituals.water.RitualWaterT2;
import theflogat.technomancy.common.rituals.water.RitualWaterT3;

/**
 * Every registered ritual, in the order the catalyst scans them ({@code RitualRegistry}).
 *
 * <p>The original used a fixed 64-slot array and returned {@code current - 1} from
 * {@code getLength()}, which is 0 when one ritual is registered — so a one-ritual install
 * registered nothing. A plain list cannot make that mistake.</p>
 */
public final class RitualRegistry {

    private static final List<Ritual> RITUALS = new ArrayList<>();

    private RitualRegistry() {
    }

    public static void register(Ritual ritual) {
        RITUALS.add(ritual);
    }

    public static List<Ritual> all() {
        return List.copyOf(RITUALS);
    }

    /** Called from common setup; safe to call once. */
    public static void bootstrap() {
        if (!RITUALS.isEmpty()) {
            return;
        }
        register(new RitualPurificationT1());
        register(new RitualPurificationT2());
        register(new RitualPurificationT3());
        register(new RitualWaterT1());
        register(new RitualWaterT2());
        register(new RitualWaterT3());
        register(new theflogat.technomancy.common.rituals.earth.RitualCaveInT1());
        register(new theflogat.technomancy.common.rituals.earth.RitualCaveInT2());
        register(new theflogat.technomancy.common.rituals.earth.RitualCaveInT3());
        register(new theflogat.technomancy.common.rituals.dark.RitualBlackHoleT1());
        register(new theflogat.technomancy.common.rituals.dark.RitualBlackHoleT2());
        register(new theflogat.technomancy.common.rituals.dark.RitualBlackHoleT3());
        register(new theflogat.technomancy.common.rituals.fire.RitualOfFireT1());
        register(new theflogat.technomancy.common.rituals.fire.RitualOfFireT2());
        register(new theflogat.technomancy.common.rituals.earth.RitualExtraction());
        register(new theflogat.technomancy.common.rituals.dark.RitualFountainExistence());
    }
}
