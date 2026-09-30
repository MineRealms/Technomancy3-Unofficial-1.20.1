package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the electric bellows as an item, a port of
 * {@code BlockElectricBellowsRenderer.renderInventoryBlock}.
 *
 * <p>Upstream's inventory path was {@code translate(-.5, -.5, -.5)} and then a call to
 * {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
 * {@code TileElectricBellows}; because that tile had no world, the renderer took its null-world
 * branch, which forced {@code facing = NORTH} and drove the inflation from a client-side sine
 * pulse. This reproduces exactly that: {@link ElectricBellowsRenderer#draw} is called with
 * {@code NORTH} and {@link ElectricBellowsRenderer#pulse}, so the item and the block are the same
 * drawing.</p>
 *
 * <p>Upstream's inventory framing needs no re-derivation here, because it comes out as the
 * identity. Its two translations are {@code translate(-.5, -.5, -.5)} and, from
 * {@code translateFromOrientation(0, 0, 0, 2)}, {@code translate(.5, -.5, .5)}, and its own
 * {@code translate(0, 1, 0)} sits between them - so the item is drawn in exactly the space
 * {@link ElectricBellowsRenderer#draw} leaves the block in, with no offset and no scale. That
 * space is the block's own {@code [0, 1]} cube, and the model already sits on its centre line:
 * the three planks span {@code x} 0.125..0.875, {@code y} 0..1 and {@code z} 0.125..0.875, the
 * nozzle adds {@code z} 0.875..1, and the bag - the only part that moves - is symmetric about
 * {@code x = z = 0.5} at every inflation. The item's {@code display} transform supplies the
 * presentation rotation and scale, as it does for a vanilla block item.</p>
 */
public final class ElectricBellowsItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static ElectricBellowsItemRenderer instance;

    private ElectricBellowsItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static ElectricBellowsItemRenderer get() {
        if (instance == null) {
            instance = new ElectricBellowsItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Minecraft client = Minecraft.getInstance();
        // The original pulsed off thePlayer.ticksExisted; there is no player in the main menu, so
        // the pulse freezes at its neutral 0.7 rather than crashing.
        float ticks = client.player == null ? 0.0F : client.player.tickCount;
        // No centring offset: upstream's inventory translations cancel, and the model is already
        // centred in the block's [0, 1] cube. NORTH: the original forced facing = 2 on the
        // throwaway tile.
        ElectricBellowsRenderer.draw(ElectricBellowsRenderer.pulse(ticks), Direction.NORTH, pose,
                buffers, light, overlay);
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return ElectricBellowsItemRenderer.get();
        }
    }
}
