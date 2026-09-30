package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the flux lamp in an inventory, a port of {@code BlockFluxLampRenderer.renderInventoryBlock}.
 *
 * <p>Upstream's inventory path is three lines: translate, then call the tile entity renderer on a
 * throwaway {@code TileFluxLamp}. The item and the block are therefore the same drawing by
 * construction rather than by two hand-kept copies, and this does the same thing by calling
 * {@link FluxLampRenderer#draw} with the fresh entity's own state - {@code placed = false} and an
 * empty tank, so the icon is the bare thirteen-box lamp with no nozzles and no tint.</p>
 *
 * <p>Without this the icon would be blank: the block's model is an empty shell now that the
 * renderer draws the whole body.</p>
 */
public final class FluxLampItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static FluxLampItemRenderer instance;

    private FluxLampItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static FluxLampItemRenderer get() {
        if (instance == null) {
            instance = new FluxLampItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        // The model is already centred on the block's own cube, so the display transform Forge has
        // applied by now is the whole of the placement.
        FluxLampRenderer.draw(false, 0, null, null, pose, buffers, light, overlay);
    }

    /** The client hook the item hands Forge through {@code Item.initializeClient}. */
    public static final class Extensions implements IClientItemExtensions {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return FluxLampItemRenderer.get();
        }
    }
}
