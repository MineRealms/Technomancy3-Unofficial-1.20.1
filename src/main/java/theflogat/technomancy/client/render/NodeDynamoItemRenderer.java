package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the node dynamo as an item, the way {@code BlockNodeDynamoRenderer.renderInventoryBlock}
 * drew it in 1.7.10: by handing the whole job to the block's own renderer.
 *
 * <p>Upstream's inventory path was {@code translate(-0.5, -0.5, -0.5);} followed by a call to
 * {@code renderTileEntityAt} on a throwaway {@code TileNodeDynamo} - the item and the block were
 * the same drawing by construction. It applied no extra rotation, so none is added here; the
 * item's {@code display} block still supplies the presentation turn, which keeps the icon at the
 * same angle as every other block item.</p>
 *
 * <p>What is <em>not</em> kept is upstream's framing constants: those were tuned for 1.7.10's
 * inventory space, so they are re-derived below for 1.20.1's item space, which is block space
 * with the origin at the block's minimum corner. In the space {@link NodeDynamoRenderer#draw}
 * leaves the model in, a model point {@code (mx, my, mz)} lands at
 * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}, and the twenty-two boxes span model
 * {@code x} -8.5..8.5, {@code y} 8.1292..24 and {@code z} -8.8284..8.8284, the last being
 * {@code Post2}/{@code Post4}'s 45 degree reach. So the block-space centre is
 * {@code (0.5, 0.4959624, 0.5)} and the largest extent is the z one, 1.1035534 blocks.</p>
 */
public final class NodeDynamoItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Centre of the machine's bounding box in the space {@code draw} leaves it in. */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 0.4959624F;
    private static final float CENTRE_Z = 0.5F;

    /**
     * {@code 1 / 1.1035534}, the z extent. The machine is almost exactly one block across, so
     * unlike the node fabricator it needs only a hair of shrink to sit inside the item slot
     * instead of overflowing it by about five percent on each side.
     */
    private static final float SCALE = 1.0F / 1.1035534F;

    private static NodeDynamoItemRenderer instance;

    private NodeDynamoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static NodeDynamoItemRenderer get() {
        if (instance == null) {
            instance = new NodeDynamoItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the icon box, centring the machine in it. The original added no rotation
        // of its own, so neither does this.
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-CENTRE_X, -CENTRE_Y, -CENTRE_Z);
        NodeDynamoRenderer.draw(pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return NodeDynamoItemRenderer.get();
        }
    }
}
