package theflogat.technomancy.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.compat.botania.BotaniaPresence;

/**
 * The Mana Exchanger against a real Botania mana pool: the pool must sit directly on top, and a
 * full Q buffer buys one 1,000-mana / 1-bucket step per tick in either direction.
 *
 * <p>This holder stays free of Botania types on purpose. Forge resolves every method descriptor of
 * a {@code @GameTestHolder} class when it registers the batch, so a {@code ManaPool} return type
 * anywhere in here would make the mod fail to start without Botania. The real bodies live in
 * {@link ManaExchangerBotaniaTests}, loaded reflectively only when Botania is present; without it
 * the three tests pass as no-ops.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaExchangerGameTests {

    private static final String BATCH = "technom_botania";

    private ManaExchangerGameTests() {}

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void drawsManaIntoTheTank(GameTestHelper helper) {
        run(helper, "drawsManaIntoTheTank");
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void pushesFluidIntoThePool(GameTestHelper helper) {
        run(helper, "pushesFluidIntoThePool");
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 60)
    public static void needsAPoolOnTop(GameTestHelper helper) {
        run(helper, "needsAPoolOnTop");
    }

    private static void run(GameTestHelper helper, String method) {
        if (!BotaniaPresence.isLoaded()) {
            helper.succeed();
            return;
        }
        try {
            Class.forName("theflogat.technomancy.gametest.ManaExchangerBotaniaTests")
                    .getMethod(method, GameTestHelper.class)
                    .invoke(null, helper);
        } catch (ReflectiveOperationException exception) {
            helper.fail("Mana exchanger test " + method + " could not be invoked: " + exception);
        }
    }
}
