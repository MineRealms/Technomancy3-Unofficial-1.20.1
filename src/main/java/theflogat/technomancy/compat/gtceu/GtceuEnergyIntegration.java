package theflogat.technomancy.compat.gtceu;

import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyProtocols;

/**
 * The single entry point of the GTCEu integration.
 *
 * <p>{@code Technomancy} calls {@link #install} only when {@link GtceuPresence#isLoaded()} is
 * true. Neither that gate nor the call site mentions a {@code com.gregtechceu} type, and this
 * class only names our own adapter classes, so the JVM never has to resolve a GT class in a game
 * without GTCEu — the class-scanning GameTest enforces exactly that layering.</p>
 */
public final class GtceuEnergyIntegration {

    private static boolean installed;

    private GtceuEnergyIntegration() {
    }

    /**
     * Registers the EU protocol view for every machine and reports FE/EU rate disagreements.
     * Repeated calls do nothing, so no machine can end up with two EU views.
     *
     * @param qPerEu the session's frozen FE per EU
     */
    public static synchronized void install(long qPerEu) {
        if (installed) {
            return;
        }
        installed = true;
        EnergyProtocols.register(GtceuEnergyProtocol::new);
        GtceuRateCheck.run(qPerEu);
        Technomancy.LOGGER.info("GTCEu native EU integration enabled at {} FE per EU", qPerEu);
    }
}
