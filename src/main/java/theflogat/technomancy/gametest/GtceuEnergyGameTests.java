package theflogat.technomancy.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.compat.gtceu.GtceuPresence;

/**
 * Behaviour of the optional GTCEu EU integration. Only {@link GtceuEnergyChecks} touches the GT
 * API; this holder stays free of {@code com.gregtechceu} types so Forge can load it, reflect over
 * its methods and run it on a runtime without GTCEu, where every test skips with a logged reason
 * instead of failing.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GtceuEnergyGameTests {

    private GtceuEnergyGameTests() {}

    /** The tier table is shipped as plain data; this is the only check that it still matches GTCEu. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = "technom_gtceu")
    public static void euTierTableMatchesGtceu(GameTestHelper helper) {
        if (skipWithoutGtceu(helper, "euTierTableMatchesGtceu")) {
            return;
        }
        GtceuEnergyChecks.tierTable(helper);
    }

    /** Our {@code IEnergyContainer} views, exercised through the real GT interface and by GT's own code. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = "technom_gtceu")
    public static void energyContainerViewsAnswerCorrectly(GameTestHelper helper) {
        if (skipWithoutGtceu(helper, "energyContainerViewsAnswerCorrectly")) {
            return;
        }
        GtceuEnergyChecks.capabilityView(helper);
    }

    /** A real GT machine placed in the world receives EU packets from our active output. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = "technom_gtceu")
    public static void realGtMachineReceivesOurPackets(GameTestHelper helper) {
        if (skipWithoutGtceu(helper, "realGtMachineReceivesOurPackets")) {
            return;
        }
        GtceuEnergyChecks.exchangeWithRealMachine(helper);
    }

    /**
     * Succeeds without running the GT half when GTCEu is absent. The check itself lives in a
     * separate class, so returning here also means no GT class is ever resolved.
     */
    private static boolean skipWithoutGtceu(GameTestHelper helper, String test) {
        if (GtceuPresence.isLoaded()) {
            return false;
        }
        Technomancy.LOGGER.info("GameTest {} skipped: GTCEu is not on this runtime", test);
        helper.succeed();
        return true;
    }
}
