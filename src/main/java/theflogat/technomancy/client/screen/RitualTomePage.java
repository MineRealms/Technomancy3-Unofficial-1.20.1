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

import java.util.Collections;
import java.util.List;

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

    public void render(Font font, GuiGraphics g, int left, int top, float scale) {
        int halfLeft = left + (right ? 136 : 30);
        int halfTop = top + 12;
        switch (kind) {
            case TEXT -> renderText(font, g, halfLeft, halfTop, scale);
            case IMAGE -> renderImage(g, halfLeft, top, scale);
            case RECIPE -> renderRecipe(font, g, halfLeft, top, scale);
        }
    }

    private void renderText(Font font, GuiGraphics g, int halfLeft, int halfTop, float scale) {
        int maxLength = 100;
        int lineHeight = font.lineHeight + 1;
        int y = 0;
        for (String line : lines) {
            int x = 0;
            String[] words = line.split(" ");
            for (int wi = 0; wi < words.length; wi++) {
                String word = words[wi];
                int w = font.width(word);
                if (x + w > maxLength && x > 0) {
                    y += lineHeight;
                    x = 0;
                }
                int dx = Math.round(x * scale);
                int dy = Math.round(y * scale);
                g.drawString(font, word, halfLeft + dx, halfTop + dy, 0x000000);
                x += w + font.width(" ");
            }
            y += lineHeight;
        }
    }

    private void renderImage(GuiGraphics g, int halfLeft, int top, float scale) {
        int ix = halfLeft + Math.round(imageOffsetX * scale);
        int iy = top + Math.round(imageOffsetY * scale);
        g.blit(image, ix, iy, 0, 0, 32, 32, 32, 32);
    }

    private void renderRecipe(Font font, GuiGraphics g, int halfLeft, int top, float scale) {
        Level level = Minecraft.getInstance().level;
        if (level == null || result == null || result.isEmpty()) {
            return;
        }
        CraftingRecipe recipe = findRecipe(level, result);
        if (recipe == null) {
            return;
        }
        int cell = Math.round(18 * scale);
        int gridLeft = halfLeft + Math.round(0 * scale);
        int gridTop = top + Math.round(82 * scale);
        int resultLeft = halfLeft + Math.round(74 * scale);
        int resultTop = top + Math.round(100 * scale);
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