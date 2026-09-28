package theflogat.technomancy.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.rituals.RitualRegistry;

import java.util.List;

/**
 * The ritual tome's page. One page per ritual, showing the core kind and the frame kinds of its
 * three tiers; left/right arrows or the arrow keys turn pages.
 */
public final class RitualTomeScreen extends Screen {

    private static final int LINE_HEIGHT = 12;
    private final List<Ritual> rituals = RitualRegistry.all();
    private int page;

    public RitualTomeScreen() {
        super(Component.translatable("item.technom.ritual_tome"));
    }

    private void turn(int delta) {
        if (!rituals.isEmpty()) {
            page = Math.floorMod(page + delta, rituals.size());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        turn(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) {
            turn(-1);
            return true;
        }
        if (keyCode == 262) {
            turn(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = 20;
        int y = 30;
        graphics.drawCenteredString(font, title, width / 2, y - 18, 0xFFFFFF);
        if (rituals.isEmpty()) {
            graphics.drawString(font, "no rituals registered", x, y, 0xFF8080);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        Ritual ritual = rituals.get(page);
        graphics.drawString(font, "Ritual " + (page + 1) + " / " + rituals.size(), x, y, 0xA0A0A0);
        y += LINE_HEIGHT * 2;
        graphics.drawString(font, "core: " + ritual.core().name(), x, y, 0xFFFFFF);
        y += LINE_HEIGHT;
        for (int tier = 0; tier < 3; tier++) {
            Ritual.Type frame = ritual.frame(tier);
            graphics.drawString(font, "tier " + (tier + 1) + ": " + (frame == null ? "empty" : frame.name()),
                    x, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
        graphics.drawString(font, "< scroll or arrows >", x, height - 20, 0x808080);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
