package theflogat.technomancy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import theflogat.technomancy.client.screen.RitualTomeContent.Entry;
import theflogat.technomancy.client.screen.RitualTomeContent.Tab;

/**
 * The ritual tome's page system, modelled on the 1.7.10 {@code GuiRitualTome}: a 256×256 book
 * with eight tabs on the left edge, labelled entries in the active tab, and a stack of two-page
 * spreads the entry navigates with next/prev buttons at the bottom corners. The book auto-fits
 * the window so it never overflows regardless of GUI scale.
 */
public final class RitualTomeScreen extends Screen {

    /** The book's nominal pixel size (the textures are 256×256). */
    private static final int BOOK_SIZE = 256;
    /** Margin reserved around the book. */
    private static final int MARGIN = 16;

    private final java.util.List<Tab> tabs = RitualTomeContent.build();
    private int activeTab = -1;
    private int activeEntry = -1;
    private int activePage = 0;

    public RitualTomeScreen() {
        super(Component.translatable("item.technom.ritual_tome"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Auto-fit scale, capped at 1.0 so the book never exceeds its design size. */
    private float computeScale() {
        float maxW = (width - MARGIN) / (float) BOOK_SIZE;
        float maxH = (height - MARGIN) / (float) BOOK_SIZE;
        float s = Math.min(maxW, maxH);
        if (s > 1.0f) s = 1.0f;
        return s;
    }

    private int bookLeft() {
        return (width - BOOK_SIZE) / 2;
    }

    private int bookTop() {
        return (height - BOOK_SIZE) / 2;
    }

    /** Transform screen mouse coord into book-local coord under the current scale. */
    private static int toBookX(double mouseX, int left, float scale) {
        return (int) Math.floor((mouseX - left) / scale);
    }

    private static int toBookY(double mouseY, int top, float scale) {
        return (int) Math.floor((mouseY - top) / scale);
    }

    private boolean inBookRect(double mouseX, double mouseY, int left, int top, float scale,
                                int x, int y, int w, int h) {
        int bx = toBookX(mouseX, left, scale);
        int by = toBookY(mouseY, top, scale);
        return bx >= x && bx < x + w && by >= y && by < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeTab >= 0 && activeEntry >= 0) {
            turnPage(delta > 0 ? -1 : 1);
        } else {
            cycleTab(delta > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // LEFT_ARROW = 263, RIGHT_ARROW = 262
        if (keyCode == 263) {
            turnPage(-1);
            return true;
        }
        if (keyCode == 262) {
            turnPage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void turnPage(int delta) {
        if (activeTab < 0 || activeEntry < 0) return;
        Entry e = tabs.get(activeTab).entries.get(activeEntry);
        if (e.spreads.isEmpty()) return;
        activePage = Math.floorMod(activePage + delta, e.spreads.size());
    }

    private void cycleTab(int delta) {
        if (tabs.isEmpty()) return;
        int next = (activeTab < 0 ? 0 : Math.floorMod(activeTab + delta, tabs.size()));
        openTab(next);
    }

    private void openTab(int tabIdx) {
        activeTab = tabIdx;
        activeEntry = -1;
        activePage = 0;
    }

    private void openEntry(int entryIdx) {
        activeEntry = entryIdx;
        activePage = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = computeScale();
        int left = bookLeft();
        int top = bookTop();
        int bx = toBookX(mouseX, left, scale);
        int by = toBookY(mouseY, top, scale);

        // Page-turn buttons: (18,230) next, (220,230) prev (in book coords; 16x16 each).
        if (activeTab >= 0 && activeEntry >= 0) {
            if (bx >= 18 && bx < 18 + 16 && by >= 230 && by < 230 + 16) {
                turnPage(1);
                return true;
            }
            if (bx >= 220 && bx < 220 + 16 && by >= 230 && by < 230 + 16) {
                turnPage(-1);
                return true;
            }
        }

        // Tab strip: 16x16 icons at (9, 9 + 16*i).
        for (int i = 0; i < tabs.size(); i++) {
            int tx = 9;
            int ty = 9 + i * 16;
            if (bx >= tx && bx < tx + 16 && by >= ty && by < ty + 16) {
                openTab(i);
                return true;
            }
        }

        // Entry list (only while a tab is active and nothing is open - once an entry is open the
        // list is replaced by its pages, so it must not swallow clicks either).
        if (activeTab >= 0 && activeEntry < 0) {
            int y = 12;
            Tab t = tabs.get(activeTab);
            for (int i = 0; i < t.entries.size(); i++) {
                int x = i > 15 ? 50 : 30;
                int width = font.width(t.entries.get(i).label);
                int height = font.lineHeight + 1;
                if (bx >= x && bx < x + width && by >= y && by < y + height) {
                    openEntry(i);
                    return true;
                }
                y += height;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float scale = computeScale();
        int left = bookLeft();
        int top = bookTop();

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1);

        drawBookBackground(g);
        drawTabStrip(g);
        drawActiveTabContent(g, mouseX, mouseY, left, top, scale);
        drawPageTurnButtons(g, mouseX, mouseY, scale);

        g.pose().popPose();
    }

    private void drawBookBackground(GuiGraphics g) {
        g.blit(RitualTomeContent.BACK_TEXTURE, 0, 0, 0, 0, BOOK_SIZE, BOOK_SIZE, BOOK_SIZE, BOOK_SIZE);
    }

    private void drawTabStrip(GuiGraphics g) {
        // 16x16 icons stacked at (9, 9 + 16*i); the icons in tomebuttonsritual are 16x16 strips
        // laid out top-to-bottom in a single column.
        for (int i = 0; i < tabs.size(); i++) {
            int tx = 9;
            int ty = 9 + i * 16;
            g.blit(RitualTomeContent.BUTTONS_TEXTURE, tx, ty, 0, i * 16, 16, 16, BOOK_SIZE, BOOK_SIZE);
        }
    }

    private void drawActiveTabContent(GuiGraphics g, int mouseX, int mouseY, int left, int top, float scale) {
        if (activeTab < 0) {
            // Tab hover label drawn at the mouse position, like upstream.
            int bx = toBookX(mouseX, left, scale);
            int by = toBookY(mouseY, top, scale);
            for (Tab t : tabs) {
                int tx = 9;
                int ty = 9 + t.iconIndex * 16;
                if (bx >= tx && bx < tx + 16 && by >= ty && by < ty + 16) {
                    g.drawString(font, t.name, bx + 4, by + 4, 0xFFFFFF);
                    return;
                }
            }
            return;
        }
        Tab tab = tabs.get(activeTab);
        // The entry list and an open entry's pages are mutually exclusive, as they were upstream:
        // GuiTomeTemplate.drawScreen did `if (activeEntry != -1) entry.drawPage(...) else
        // drawTabs(...)`. They also share an origin - the list starts at (30, 12) and so does the
        // left page's text - so drawing both put every chapter label on top of a line of the
        // entry's own body text.
        if (activeEntry < 0) {
            int bx = toBookX(mouseX, left, scale);
            int by = toBookY(mouseY, top, scale);
            int y = 12;
            for (int i = 0; i < tab.entries.size(); i++) {
                int x = i > 15 ? 50 : 30;
                Entry e = tab.entries.get(i);
                int height = font.lineHeight + 1;
                // Upstream tinted the entry under the cursor. There is no longer a "current"
                // entry to tint: the list is only up while nothing is open.
                boolean hovered = bx >= x && bx < x + font.width(e.label)
                        && by >= y && by < y + height;
                g.drawString(font, e.label, x, y, hovered ? 0x3366FF : 0x000000);
                y += height;
            }
            return;
        }
        Entry e = tab.entries.get(activeEntry);
        if (!e.spreads.isEmpty()) {
            RitualTomePage[] spread = e.spreads.get(Math.floorMod(activePage, e.spreads.size()));
            spread[0].render(font, g, 0, 0);
            spread[1].render(font, g, 0, 0);
            String indicator = (activePage + 1) + " / " + e.spreads.size();
            g.drawString(font, indicator, BOOK_SIZE / 2 - font.width(indicator) / 2, 240, 0x404040);
        }
    }

    private void drawPageTurnButtons(GuiGraphics g, int mouseX, int mouseY, float scale) {
        if (activeTab < 0 || activeEntry < 0) return;
        boolean hoverNext = inBookRect(mouseX, mouseY, 0, 0, scale, 18, 230, 16, 16);
        boolean hoverPrev = inBookRect(mouseX, mouseY, 0, 0, scale, 220, 230, 16, 16);
        // (u=0, v=0) = next arrow; (u=16, v=0) = prev arrow (and the hover overlay).
        g.blit(RitualTomeContent.NEXT_PREV, 18, 230, 0, 0, 16, 16, BOOK_SIZE, BOOK_SIZE);
        g.blit(RitualTomeContent.NEXT_PREV, 220, 230, 16, 0, 16, 16, BOOK_SIZE, BOOK_SIZE);
        if (hoverNext) {
            g.blit(RitualTomeContent.NEXT_PREV, 18, 230, 16, 0, 16, 16, BOOK_SIZE, BOOK_SIZE);
        }
        if (hoverPrev) {
            g.blit(RitualTomeContent.NEXT_PREV, 220, 230, 16, 0, 16, 16, BOOK_SIZE, BOOK_SIZE);
        }
    }
}