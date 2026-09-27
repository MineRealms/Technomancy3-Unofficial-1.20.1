package theflogat.technomancy.compat.gtceu;

import net.minecraftforge.fml.ModList;

/**
 * Presence gate for the optional GTCEu integration.
 *
 * <p>This class must stay free of {@code com.gregtechceu} types: common code asks it before
 * calling an isolated GT bootstrap, so it has to load and run when GTCEu is absent. The
 * {@code GtceuIsolationGameTests} scan enforces that only GT adapter classes in this package
 * (never this class) link against GT.</p>
 */
public final class GtceuPresence {
    public static final String MOD_ID = "gtceu";

    private GtceuPresence() {}

    /** Whether GTCEu is loaded. Valid from mod construction onwards. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
