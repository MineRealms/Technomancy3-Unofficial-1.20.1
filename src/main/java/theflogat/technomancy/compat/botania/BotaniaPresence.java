package theflogat.technomancy.compat.botania;

import net.minecraftforge.fml.ModList;

/**
 * Presence gate for the optional Botania module.
 *
 * <p>This class must stay free of {@code vazkii.botania} types: common code asks it before calling
 * {@link BotaniaContent#install()}, so it has to load and run when Botania is absent.</p>
 */
public final class BotaniaPresence {

    public static final String MOD_ID = "botania";

    private BotaniaPresence() {
    }

    /** Whether Botania is loaded. Valid from mod construction onwards. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
