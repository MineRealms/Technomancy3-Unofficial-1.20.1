package theflogat.technomancy.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.menu.ProcessorMenu;

/**
 * {@code GuiProcessorTC}: the shared processor screen.
 *
 * <p>Same texture and geometry as the original — 175x167, the progress arrow at (74, 25) taken
 * from (175, 0) of the sheet. The arrow fills as the job advances; the original's counter ran
 * down from 60, so its arrow emptied instead, which read as a machine going backwards.</p>
 */
public class ProcessorScreen extends AbstractContainerScreen<ProcessorMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/gui/processortc.png");

    private static final int ARROW_X = 74;
    private static final int ARROW_Y = 25;
    private static final int ARROW_WIDTH = 20;
    private static final int ARROW_HEIGHT = 20;

    public ProcessorScreen(ProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 175;
        imageHeight = 167;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int filled = menu.progress() * ARROW_WIDTH / menu.maxProgress();
        if (filled > 0) {
            graphics.blit(TEXTURE, leftPos + ARROW_X, topPos + ARROW_Y, imageWidth, 0,
                    filled, ARROW_HEIGHT);
        }
    }

    /**
     * The fuel line is new. The original showed the buffer only through a Waila handler, so
     * without that mod there was no way to see why a processor had stopped.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component fuel = Component.translatable("technom.processor.fuel", menu.fuel(), menu.fuelCapacity());
        graphics.drawString(font, fuel, titleLabelX, titleLabelY + 11, 0x404040, false);
    }
}
