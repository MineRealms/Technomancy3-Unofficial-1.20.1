package theflogat.technomancy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import theflogat.technomancy.Technomancy;

/**
 * The Existence gauge: a small filled column at the top left, coloured by the dominant affinity,
 * fed by {@link theflogat.technomancy.common.network.ExistenceSyncPacket}.
 */
public final class ExistenceHud {

    private static int level = 1;
    private static int power = 1;
    private static int affinity = 5;

    private ExistenceHud() {
    }

    public static void accept(int newLevel, int newPower, int newAffinity) {
        level = newLevel;
        power = newPower;
        affinity = newAffinity;
    }

    public static void init(net.minecraftforge.eventbus.api.IEventBus modBus) {
        modBus.addListener(ExistenceHud::registerOverlays);
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("technom_existence", (IGuiOverlay) ExistenceHud::render);
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
            float partialTick, int width, int height) {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().options.hideGui) {
            return;
        }
        int x = 6;
        int y = 6;
        int barWidth = 6;
        int barHeight = 20;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0x80000000);
        float ratio = level <= 0 ? 0.0F : Math.min(1.0F, (float) power / (level * 100.0F));
        int filled = (int) (barHeight * ratio);
        int colour = 0xFF000000 | theflogat.technomancy.common.player.Affinity.byId(affinity).color();
        graphics.fill(x, y + barHeight - filled, x + barWidth, y + barHeight, colour);
    }

    /** Kept so a client-only tick can clear the gauge on logout. */
    public static void clear() {
        level = 1;
        power = 1;
        affinity = 5;
    }
}
