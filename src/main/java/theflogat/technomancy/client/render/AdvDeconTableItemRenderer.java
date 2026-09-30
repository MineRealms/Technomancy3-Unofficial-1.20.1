package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the advanced deconstruction table as an item, the way
 * {@code BlockAdvDeconTableRenderer.renderInventoryBlock} did in 1.7.10: by handing the whole job to
 * the block's own renderer.
 *
 * <p>That is not a stylistic choice. Upstream's inventory renderer translated by
 * {@code (-0.5, -0.5, -0.5)} and then called {@code renderTileEntityAt} on a throwaway
 * {@code TileAdvDeconTable} - the item and the block were the same drawing by construction. A
 * hand-written JSON model cannot reproduce it: the table is nine boxes with per-box rotations, and
 * the original never exposed a block model for it at all.</p>
 *
 * <p>The throwaway entity had no world, so upstream's floating-item half was skipped and the icon
 * is the bare table. That is reproduced below by delegating with an empty stack and no animation -
 * the block's own renderer does the drawing.</p>
 *
 * <p>Upstream's {@code (-0.5, -0.5, -0.5)} was tuned for 1.7.10's inventory space, whose origin is
 * the block's centre; 1.20.1's item space has the origin at the block's minimum corner, and the
 * display transform already rotates about the block centre. The model's own bounding box is
 * {@code x} 0..1, {@code y} 0..15/16, {@code z} 0..1, so only its vertical centre is off - by
 * 1/32 - and that is the whole of the centring shift below.</p>
 */
public final class AdvDeconTableItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The centre of the table's bounding box in the space {@link AdvDeconTableRenderer#draw} leaves
     * it in, with an empty slot. That chain maps a model point to
     * {@code (0.5 - x/16, 1.5 - y/16, 0.5 + z/16)}, and the nine boxes span {@code x} -8..8,
     * {@code y} 9..24 and {@code z} -8..8 model units - so the box is centred in {@code x} and
     * {@code z} and sits 1/32 below the block centre in {@code y}.
     */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 0.46875F;
    private static final float CENTRE_Z = 0.5F;

    private static AdvDeconTableItemRenderer instance;

    private AdvDeconTableItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static AdvDeconTableItemRenderer get() {
        if (instance == null) {
            instance = new AdvDeconTableItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the icon box, shifting the table's own centre onto the block centre.
        pose.translate(0.5F - CENTRE_X, 0.5F - CENTRE_Y, 0.5F - CENTRE_Z);
        // A fresh TileAdvDeconTable had no world and an empty slot, so the icon is the bare model.
        AdvDeconTableRenderer.draw(ItemStack.EMPTY, 0.0F, null, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return AdvDeconTableItemRenderer.get();
        }
    }
}
