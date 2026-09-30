package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the biome morpher as an item, the way {@code BlockBiomeMorpherRenderer.renderInventoryBlock}
 * drew it in 1.7.10: by handing the whole job to the block's own renderer.
 *
 * <p>Upstream's inventory path is {@code glTranslatef(-0.5F, -0.5F, -0.5F)} followed by a call to
 * {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway {@code TileBiomeMorpher} -
 * the item and the block were the same drawing by construction. That translate only moved the
 * block's {@code [0, 1]} cube onto 1.7.10's inventory origin; in 1.20.1 the item's block space is
 * already corner-origin, so the equivalent here is a centring translate and nothing else.</p>
 *
 * <p>The model needs no rescale, unlike the node fabricator's: it is essentially one block, not
 * 2.875. The block renderer leaves it at {@code x} and {@code z} 0..1 and {@code y} 0..17/16, so
 * its centre is {@code (0.5, 0.53125, 0.5)} and only the {@code y} axis has anything to recentre.
 * Shrinking it to fit the cube exactly would make the icon a different size from the block that
 * gets placed.</p>
 */
public final class BiomeMorpherItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The centre of the model's bounding box in the space {@link BiomeMorpherRenderer#draw} leaves
     * it in. Under that chain a model point {@code (mx, my, mz)} lands at block-local
     * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}, and the fourteen boxes span {@code x} -8..8,
     * {@code z} -8..8 and {@code y} 7..24 - the posts reach one sixteenth above the top plate, so
     * the box runs {@code y} 0..17/16 and its centre is 17/32.
     */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 0.53125F;
    private static final float CENTRE_Z = 0.5F;

    private static BiomeMorpherItemRenderer instance;

    private BiomeMorpherItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static BiomeMorpherItemRenderer get() {
        if (instance == null) {
            instance = new BiomeMorpherItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the icon box: put the model's centre on the block's own centre. The item's
        // display block still supplies the presentation rotation, so the icon sits at the same
        // angle as every other block item.
        pose.translate(0.5F - CENTRE_X, 0.5F - CENTRE_Y, 0.5F - CENTRE_Z);
        // The morpher has no animation and reads no tile state, so draw() takes nothing else.
        BiomeMorpherRenderer.draw(pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return BiomeMorpherItemRenderer.get();
        }
    }
}
