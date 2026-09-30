package theflogat.technomancy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One half-page in the ritual tome. The tome treats a spread as two halves: a left half
 * ({@link #right} = false) starting at {@code left + 30} and a right half
 * ({@link #right} = true) starting at {@code left + 136}, mirroring the upstream
 * {@code GuiTomeTemplate} layout.
 */
public final class RitualTomePage {

    public enum Kind {
        TEXT, IMAGE, RECIPE
    }

    public final boolean right;
    public final Kind kind;

    private final List<String> lines;

    public final ResourceLocation image;
    public final int imageOffsetX;
    public final int imageOffsetY;

    public final ItemStack result;

    private RitualTomePage(boolean right, Kind kind, List<String> lines,
                           ResourceLocation image, int offX, int offY,
                           ItemStack result) {
        this.right = right;
        this.kind = kind;
        this.lines = lines == null ? List.of() : Collections.unmodifiableList(lines);
        this.image = image;
        this.imageOffsetX = offX;
        this.imageOffsetY = offY;
        this.result = result;
    }

    public static RitualTomePage text(boolean right, String paragraph) {
        return new RitualTomePage(right, Kind.TEXT, List.of(paragraph.split("\\n")), null, 0, 0, null);
    }

    public static RitualTomePage text(boolean right, List<String> lines) {
        return new RitualTomePage(right, Kind.TEXT, lines, null, 0, 0, null);
    }

    public static RitualTomePage image(boolean right, ResourceLocation image, int offX, int offY) {
        return new RitualTomePage(right, Kind.IMAGE, List.of(), image, offX, offY, null);
    }

    public static RitualTomePage recipe(boolean right, ItemStack result) {
        return new RitualTomePage(right, Kind.RECIPE, List.of(), null, 0, 0, result);
    }

    /**
     * Draws this half-page. {@code left}/{@code top} are the book's origin; the caller has already
     * scaled the pose to fit the window, so everything here is in book units and must not be
     * multiplied by that scale a second time. (It used to be, which pushed the page's contents
     * towards its top-left corner by a factor of {@code scale} whenever the window was too small
     * for the book's 256×256 design size.)
     */
    public void render(Font font, GuiGraphics g, int left, int top) {
        int halfLeft = left + (right ? 136 : 30);
        int halfTop = top + 12;
        switch (kind) {
            case TEXT -> renderText(font, g, halfLeft, halfTop);
            case IMAGE -> renderImage(g, halfLeft, top);
            case RECIPE -> renderRecipe(font, g, halfLeft, top);
        }
    }

    private void renderText(Font font, GuiGraphics g, int halfLeft, int halfTop) {
        int maxLength = 100;
        int lineHeight = font.lineHeight + 1;
        int y = 0;
        for (String paragraph : lines) {
            for (String line : wrap(paragraph, maxLength, font::width)) {
                g.drawString(font, line, halfLeft, halfTop + y, 0x000000);
                y += lineHeight;
            }
        }
    }

    /**
     * Greedy line wrap, in pixels.
     *
     * <p>The width of a run of characters is injected rather than read from a {@link Font} so the
     * routine can be tested without a game. It has to work for two kinds of text: the English
     * source, which has spaces to break at, and the Chinese translation, which does not. So it
     * breaks at the last space on the line when there is one and falls back to breaking between
     * characters when there is not. The old version split on {@code " "} and could not wrap an
     * unspaced line at all, which would have run Chinese text straight off the page.</p>
     *
     * <p>A character wider than {@code maxLength} on its own is still emitted, on a line of its
     * own, rather than looping forever.</p>
     *
     * <p>When the break falls on a space the space is dropped rather than carried onto the next
     * line. That also covers the case of a space landing exactly on the break, which would
     * otherwise be emitted as a line of its own - a blank line in the middle of a paragraph reads
     * as a mistake, and a half page only has about twenty lines to spend.</p>
     */
    static List<String> wrap(String paragraph, int maxLength, ToIntFunction<String> width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        int lastSpace = -1;
        for (int i = 0; i < paragraph.length(); i++) {
            String ch = String.valueOf(paragraph.charAt(i));
            int chWidth = width.applyAsInt(ch);
            if (lineWidth + chWidth > maxLength && line.length() > 0) {
                if (lastSpace > 0) {
                    // lastSpace is the index just past the space, so [lastSpace, end) is the
                    // part of the word that has to move down, with the space already gone.
                    emit(out, line.substring(0, lastSpace));
                    String carry = line.substring(lastSpace);
                    line.setLength(0);
                    line.append(carry);
                    lineWidth = width.applyAsInt(carry);
                } else {
                    emit(out, line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                lastSpace = -1;
            }
            line.append(ch);
            lineWidth += chWidth;
            if (" ".equals(ch)) {
                lastSpace = line.length();
            }
        }
        emit(out, line.toString());
        if (out.isEmpty()) {
            // An empty paragraph is still a line; the book has to advance the cursor.
            out.add("");
        }
        return out;
    }

    /** Adds a wrapped line unless it came out blank; see the space-on-the-break note above. */
    private static void emit(List<String> out, String line) {
        String trimmed = line.stripTrailing();
        if (!trimmed.isEmpty()) {
            out.add(trimmed);
        }
    }

    private void renderImage(GuiGraphics g, int halfLeft, int top) {
        g.blit(image, halfLeft + imageOffsetX, top + imageOffsetY, 0, 0, 32, 32, 32, 32);
    }

    private void renderRecipe(Font font, GuiGraphics g, int halfLeft, int top) {
        Level level = Minecraft.getInstance().level;
        if (level == null || result == null || result.isEmpty()) {
            return;
        }
        CraftingRecipe recipe = findRecipe(level, result);
        if (recipe == null) {
            return;
        }
        int cell = 18;
        int gridLeft = halfLeft;
        int gridTop = top + 82;
        int resultLeft = halfLeft + 74;
        int resultTop = top + 100;
        if (recipe instanceof ShapedRecipe shaped) {
            int w = shaped.getWidth();
            int h = shaped.getHeight();
            NonNullList<Ingredient> ingredients = shaped.getIngredients();
            int xOff = (3 - w) / 2;
            int yOff = (3 - h) / 2;
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    Ingredient ing = ingredients.get(row * w + col);
                    if (ing == null || ing.isEmpty()) continue;
                    drawItem(g, font, firstStack(ing),
                            gridLeft + (col + xOff) * cell, gridTop + (row + yOff) * cell);
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            NonNullList<Ingredient> ingredients = shapeless.getIngredients();
            int row = 0;
            int col = 0;
            for (Ingredient ing : ingredients) {
                if (ing == null || ing.isEmpty()) continue;
                drawItem(g, font, firstStack(ing),
                        gridLeft + col * cell, gridTop + row * cell);
                col++;
                if (col >= 3) {
                    col = 0;
                    row++;
                }
            }
        }
        RenderSystem.enableBlend();
        drawItem(g, font, result, resultLeft, resultTop);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CraftingRecipe findRecipe(Level level, ItemStack result) {
        for (Recipe r : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(r instanceof CraftingRecipe cr)) continue;
            ItemStack out = cr.getResultItem(level.registryAccess());
            if (ItemStack.isSameItemSameTags(out, result)
                    && out.getCount() == result.getCount()) {
                return cr;
            }
        }
        return null;
    }

    private static ItemStack firstStack(Ingredient ing) {
        ItemStack[] items = ing.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }

    private static void drawItem(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            g.drawString(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFF, true);
        }
    }
}