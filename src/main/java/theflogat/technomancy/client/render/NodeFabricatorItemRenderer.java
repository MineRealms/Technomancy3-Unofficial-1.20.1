package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the node fabricator as an item, the way {@code BlockNodeGeneratorRenderer.renderInventoryBlock}
 * drew it in 1.7.10: by handing the whole job to the block's own renderer.
 *
 * <p>That is not a stylistic choice. Upstream's inventory renderer was
 * {@code translate(-0.2, -1, -0.4); scale(0.5); rotateY(-90);} followed by a call to
 * {@code renderTileEntityAt} on a throwaway {@code TileNodeGenerator} - the item and the block
 * were the same drawing by construction. A hand-written JSON model cannot reproduce that: the
 * machine is not axis-aligned (the core carries a 45 degree X rotation), it is 2.875 blocks tall,
 * and it spans {@code y} 0..24, all of which put it outside what a block model should express.
 * Two attempts at a JSON model in this repo got the orientation and the face assignment wrong,
 * which is exactly the drift this class exists to make impossible.</p>
 *
 * <p>What is kept from upstream is every rotation it applied, including the extra
 * {@code rotateY(-90)} that its inventory path adds on top of the block's own {@code rotateY(-90)}.
 * What is <em>not</em> kept is its framing constants: those were tuned for 1.7.10's inventory
 * space, so they are re-derived below for 1.20.1's item space, which is block space with the
 * origin at the block's minimum corner. The item's {@code display} block still supplies the
 * presentation rotation, so the icon sits at the same angle as every other block item.</p>
 */
public final class NodeFabricatorItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The centre of the machine's bounding box in the space {@link NodeFabricatorRenderer#draw}
     * leaves it in, with {@code facing = NORTH} and no spin. The transform chain there is
     * {@code (0.5 + z, 1.5 - y, 0.5 + x)} over model coordinates in blocks, and the nine upstream
     * boxes span {@code x} -16..8, {@code y} -22..24 and {@code z} -22..22 model units - the core's
     * 45 degree rotation takes its 26-unit square to 13*sqrt(2) and stays inside that.
     */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 1.4375F;
    private static final float CENTRE_Z = 0.25F;

    /**
     * {@code 1 / 2.875}, the tallest extent, so the icon comes out about the size of a vanilla
     * block item rather than overflowing the slot.
     */
    private static final float SCALE = 1.0F / 2.875F;

    private static NodeFabricatorItemRenderer instance;

    private NodeFabricatorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static NodeFabricatorItemRenderer get() {
        if (instance == null) {
            instance = new NodeFabricatorItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the icon box, centring the machine and then applying upstream's turn.
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(SCALE, SCALE, SCALE);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.translate(-CENTRE_X, -CENTRE_Y, -CENTRE_Z);
        // NORTH and no spin: TileNodeGenerator defaults to facing 2 (NORTH) and rotation 0.
        NodeFabricatorRenderer.draw(Direction.NORTH, 0.0F, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return NodeFabricatorItemRenderer.get();
        }
    }
}
