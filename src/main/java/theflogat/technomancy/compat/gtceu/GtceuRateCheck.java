package theflogat.technomancy.compat.gtceu;

import com.gregtechceu.gtceu.config.ConfigHolder;
import theflogat.technomancy.Technomancy;

/**
 * Compares our frozen FE-per-EU rate with GTCEu's own conversion ratios and reports a mismatch
 * once per session.
 *
 * <p>The rate is a save-economy rule, not a tuning knob: it decides what every stored balance is
 * worth in EU. Adopting GT's value behind the player's back would silently revalue existing
 * saves, and refusing to load would punish a configuration that is merely lossy, so the only
 * action taken here is a warning that names both numbers.</p>
 */
public final class GtceuRateCheck {

    private static boolean reported;

    private GtceuRateCheck() {
    }

    /**
     * Logs at most one line about the FE/EU rate agreement. Later calls do nothing, so the
     * message cannot be repeated by a config reload or a second world load.
     *
     * @param qPerEu our frozen FE (= Q) per EU
     */
    public static synchronized void run(long qPerEu) {
        if (reported) {
            return;
        }
        reported = true;
        ConfigHolder config = ConfigHolder.INSTANCE;
        if (config == null || config.compat == null || config.compat.energy == null) {
            Technomancy.LOGGER.warn("GTCEu energy config is not initialised yet; FE/EU rate agreement unchecked");
            return;
        }
        long feToEu;
        long euToFe;
        boolean nativeBridge;
        boolean converters;
        try {
            feToEu = config.compat.energy.feToEuRatio;
            euToFe = config.compat.energy.euToFeRatio;
            nativeBridge = config.compat.energy.nativeEUToFE;
            converters = config.compat.energy.enableFEConverters;
        } catch (RuntimeException | LinkageError e) {
            // A future GTCEu may move these fields; an unreadable config must not stop the game.
            Technomancy.LOGGER.warn("Cannot read GTCEu compat.energy settings; FE/EU rate agreement unchecked", e);
            return;
        }

        if (feToEu == qPerEu && euToFe == qPerEu) {
            Technomancy.LOGGER.info("Technomancy and GTCEu agree on {} FE per EU (nativeEUToFE={}, FE converters={})",
                    qPerEu, nativeBridge, converters);
            return;
        }
        Technomancy.LOGGER.warn(
                "FE/EU rate mismatch: Technomancy stores 1 EU as {} FE, GTCEu converts FE->EU at {} and EU->FE at {}"
                        + " (nativeEUToFE={}, FE converters={}). Energy crossing the two systems changes value, and"
                        + " the EU worth of every saved Technomancy balance differs from GTCEu's, so a loop through"
                        + " GT conversion can create or destroy energy. Set Technomancy energy.fePerEu and GTCEu"
                        + " compat.energy.feToEuRatio/euToFeRatio to the same number, then keep it fixed for the save.",
                qPerEu, feToEu, euToFe, nativeBridge, converters);
        if (feToEu != euToFe) {
            Technomancy.LOGGER.warn(
                    "GTCEu's own FE/EU ratios are asymmetric ({} in, {} out); no rate on our side can make a round"
                            + " trip through GT conservative.",
                    feToEu, euToFe);
        }
    }
}
