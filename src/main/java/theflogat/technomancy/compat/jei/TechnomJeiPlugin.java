package theflogat.technomancy.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.compat.botania.BotaniaPresence;

/**
 * JEI integration.
 *
 * <p>Deliberately narrow. Every crafting, smelting and shapeless recipe this mod adds is a
 * normal data-pack recipe, which JEI already shows without being asked, so repeating them here
 * would only be a second place to keep in sync. Two things JEI cannot derive are added here
 * instead. The first is the <em>fuel value of an aspect</em>: it is a data-driven table with
 * per-biome, per-dimension and per-height conditions, and no item carries it, which is what
 * {@link EssentiaFuelCategory} exists for. The second is what a machine actually does with what
 * it is fed, which is what {@link JeiUsagePages} lists.</p>
 *
 * <p>Discovered through JEI's {@code @JeiPlugin} annotation scan, so nothing here is loaded in a
 * game without JEI - which also means no JEI class is ever resolved on a dedicated server.</p>
 */
@JeiPlugin
public final class TechnomJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new EssentiaFuelCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * Rebuilt on every recipe reload, which is also when the data pack behind it has just been
     * read, so a {@code /reload} that retunes the table is visible in JEI without a restart.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // First, and outside the table check below: a usage page says what a machine does with
        // what it is fed, which has nothing to do with the fuel table. A pack that removed every
        // fuel row would otherwise take the usage pages down with it.
        registerUsagePages(registration);

        EssentiaFuelTable table = EssentiaFuelLoader.table();
        if (table.isEmpty()) {
            // Before the first data-pack load, and in a game whose packs removed every row.
            // An empty category would be worse than none: the fallback page would claim every
            // aspect is worth nothing.
            return;
        }
        registration.addRecipes(EssentiaFuelCategory.RECIPE_TYPE, EssentiaFuelRecipe.all(table));
    }

    /**
     * Hangs a "usage" page (the one behind JEI's {@code U} key) off every item in
     * {@link JeiUsagePages#PAGES}.
     *
     * <p>JEI puts these in its built-in Information category, so there is no category of our own
     * to register for them and nothing to keep in sync when the list changes - it is one call per
     * item, and JEI decides how to present it.</p>
     *
     * <p>Rebuilt on every recipe reload along with the fuel pages, so a language change or a
     * {@code /reload} is picked up without a restart. The Botania pages are skipped rather than
     * registered empty when the module is absent: their items were never registered, so there is
     * nothing for JEI to hang the page off.</p>
     */
    private static void registerUsagePages(IRecipeRegistration registration) {
        boolean botania = BotaniaPresence.isLoaded();
        for (JeiUsagePages.Page page : JeiUsagePages.PAGES) {
            if (page.botania() && !botania) {
                continue;
            }
            registration.addIngredientInfo(page.item().get(),
                    Component.translatable(JeiUsagePages.PREFIX + page.path()));
        }
    }

    /** The dynamo is what reads the table, so it is what the page is looked up from. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(EssentiaFuelCategory.RECIPE_TYPE,
                TechnomBlocks.ESSENTIA_DYNAMO.get());
    }
}
