package theflogat.technomancy.compat.jei;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelEntry;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.essentia.fuel.FuelCondition;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The essentia dynamo's fuel table, as a JEI category.
 *
 * <p>The page has no slots, and that is not an omission. An aspect is not an item: Thaumcraft
 * carries essentia as a fluid, a jar's contents or an item's aspect tag, none of which JEI can
 * put in a slot and none of which a player would search for. So the whole page is drawn by hand
 * from the recipe record, and JEI's only job is the frame, the title and the category list.</p>
 *
 * <p>The numbers come from the loaded data pack, so a pack that retunes a value is reflected
 * here without a code change. The energy column applies
 * {@code essentiaFuelScale} the same way the dynamo does, which is why it is computed from the
 * fuel value rather than read from anywhere.</p>
 */
public final class EssentiaFuelCategory implements IRecipeCategory<EssentiaFuelRecipe> {

    public static final RecipeType<EssentiaFuelRecipe> RECIPE_TYPE =
            RecipeType.create(Technomancy.MOD_ID, "essentia_fuel", EssentiaFuelRecipe.class);

    /**
     * The page is drawn by hand, so its size is ours to pick.
     *
     * <p>The height is set by the worst row in the shipped table: a name, the base value, two
     * conditions that may each wrap onto a second line, and the random bonus. Anything longer is
     * cut off rather than allowed to spill outside the frame.</p>
     */
    private static final int WIDTH = 168;
    private static final int HEIGHT = 86;
    private static final int ICON = 16;
    private static final int LINE = 10;
    private static final int MARGIN = 3;
    private static final int TEXT = 0xFF404040;
    private static final int SUBTLE = 0xFF606060;

    /**
     * TC4R's own "no aspect here" icon, used for the fallback page.
     *
     * <p>Reached by path because {@link AspectDefinition} only offers the texture of a named
     * aspect and the fallback is by definition not one. A rename upstream costs a missing-texture
     * square on one page, nothing more.</p>
     */
    private static final ResourceLocation UNKNOWN =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_unknown.png");

    private final IDrawable icon;

    public EssentiaFuelCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemLike(TechnomBlocks.ESSENTIA_DYNAMO.get());
    }

    @Override
    public RecipeType<EssentiaFuelRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("technom.jei.essentia_fuel");
    }

    /**
     * The size of the page.
     *
     * <p>{@code getBackground()} is left at its default of {@code null}: JEI 15 draws its own
     * frame around every recipe and null-checks that drawable, and asks the category for the
     * dimensions instead. Returning a drawable here would be the deprecated path and would also
     * give the frame two sources of truth.</p>
     */
    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /** Nothing here is a JEI ingredient; {@link #draw} places everything. */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EssentiaFuelRecipe recipe, IFocusGroup focuses) {
    }

    @Override
    public void draw(EssentiaFuelRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.blit(texture(recipe), MARGIN, MARGIN, 0, 0, ICON, ICON, 32, 32);

        int y = 4;
        graphics.drawString(font, title(recipe), MARGIN + ICON + 4, y, TEXT, false);
        y += LINE + 2;
        graphics.drawString(font, base(recipe), MARGIN, y, TEXT, false);
        y += LINE + 2;

        for (EssentiaFuelEntry.ConditionalValue conditional : recipe.conditions()) {
            y = write(font, graphics, Component.translatable("technom.jei.fuel.when",
                    format(conditional.value()), describe(conditional.condition())), y);
            if (y < 0) {
                return;
            }
        }
        if (recipe.randomBonus() > 0) {
            write(font, graphics, Component.translatable("technom.jei.fuel.random",
                    format(recipe.randomBonus())), y);
        }
    }

    /**
     * Writes one message, wrapped to the page width.
     *
     * @return the next free {@code y}, or {@code -1} once the page has run out of room
     */
    private static int write(Font font, GuiGraphics graphics, Component text, int y) {
        List<FormattedCharSequence> lines = font.split(text, WIDTH - 2 * MARGIN);
        for (FormattedCharSequence line : lines) {
            if (y > HEIGHT - LINE) {
                return -1;
            }
            graphics.drawString(font, line, MARGIN, y, SUBTLE, false);
            y += LINE;
        }
        return y + 1;
    }

    private static Component title(EssentiaFuelRecipe recipe) {
        return recipe.aspect() == null
                ? Component.translatable("technom.jei.fuel.any")
                : AspectApi.tooltipName(recipe.aspect());
    }

    private static ResourceLocation texture(EssentiaFuelRecipe recipe) {
        return recipe.aspect() == null
                ? UNKNOWN
                : AspectApi.registry().get(recipe.aspect()).map(AspectDefinition::texture).orElse(UNKNOWN);
    }

    private static Component base(EssentiaFuelRecipe recipe) {
        return Component.translatable("technom.jei.fuel.base", format(recipe.value()),
                format(energy(recipe.value())));
    }

    /** Q one unit is worth at the configured fuel scale, exactly as the dynamo computes it. */
    private static long energy(int fuelValue) {
        return EssentiaFuelTable.energyPerUnit(fuelValue, TechnomancyConfig.ESSENTIA_FUEL_SCALE.get());
    }

    private static Component describe(FuelCondition condition) {
        if (condition instanceof FuelCondition.BiomeTag tag) {
            return Component.translatable("technom.jei.condition.biome", tag.tag().toString());
        }
        if (condition instanceof FuelCondition.Dimension dimension) {
            return Component.translatable("technom.jei.condition.dimension", dimension.dimension().toString());
        }
        if (condition instanceof FuelCondition.Height height) {
            Component bounds = heightBounds(height);
            return height.anchor() == FuelCondition.Height.Anchor.SEA_LEVEL
                    ? Component.translatable("technom.jei.condition.from_sea_level", bounds)
                    : bounds;
        }
        if (condition instanceof FuelCondition.SlimeChunk) {
            return Component.translatable("technom.jei.condition.slime");
        }
        if (condition instanceof FuelCondition.Daytime daytime) {
            return Component.translatable(daytime.day()
                    ? "technom.jei.condition.day"
                    : "technom.jei.condition.night");
        }
        // A condition added by a data pack this build does not know about. Show it rather than
        // drop it, so the row still explains why its value changes.
        return Component.literal(condition.toString());
    }

    private static Component heightBounds(FuelCondition.Height height) {
        Component above = height.above()
                .map(offset -> Component.translatable("technom.jei.condition.above", offset))
                .orElse(null);
        Component below = height.below()
                .map(offset -> Component.translatable("technom.jei.condition.below", offset))
                .orElse(null);
        if (above == null) {
            return below;
        }
        return below == null
                ? above
                : Component.translatable("technom.jei.condition.and", above, below);
    }

    /** Longs and ints both read badly unseparated at these magnitudes. */
    private static String format(long value) {
        return String.format("%,d", value);
    }
}
