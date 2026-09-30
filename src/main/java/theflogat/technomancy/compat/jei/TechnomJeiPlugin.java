package theflogat.technomancy.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * JEI integration.
 *
 * <p>Deliberately narrow. Every crafting, smelting and shapeless recipe this mod adds is a
 * normal data-pack recipe, which JEI already shows without being asked, so repeating them here
 * would only be a second place to keep in sync. The one thing JEI cannot derive is the
 * <em>fuel value of an aspect</em>: it is a data-driven table with per-biome, per-dimension and
 * per-height conditions, and no item carries it. That is what
 * {@link EssentiaFuelCategory} exists for.</p>
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
        EssentiaFuelTable table = EssentiaFuelLoader.table();
        if (table.isEmpty()) {
            // Before the first data-pack load, and in a game whose packs removed every row.
            // An empty category would be worse than none: the fallback page would claim every
            // aspect is worth nothing.
            return;
        }
        registration.addRecipes(EssentiaFuelCategory.RECIPE_TYPE, EssentiaFuelRecipe.all(table));
    }

    /** The dynamo is what reads the table, so it is what the page is looked up from. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(EssentiaFuelCategory.RECIPE_TYPE,
                TechnomBlocks.ESSENTIA_DYNAMO.get());
    }
}
