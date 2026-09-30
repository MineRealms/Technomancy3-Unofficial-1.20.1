package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import theflogat.technomancy.common.blocks.technom.existence.ExistenceBurnerBlock;

/**
 * Draws the existence burner as an item, the way
 * {@code BlockExistenceBurnerRenderer.renderInventoryBlock} did in 1.7.10: by handing the whole job
 * to the block's own renderer.
 *
 * <p>That is not a stylistic choice. Upstream's inventory renderer translated by
 * {@code (-0.5, -0.5, -0.5)} and then called {@code renderTileEntityAt} on a throwaway burner whose
 * metadata picked the variant - the item and the block were the same drawing by construction. A
 * hand-written JSON model cannot reproduce it: the machine is a stack of plates and a tinted cube
 * that a block model cannot colour.</p>
 *
 * <p>Upstream keyed the variant off the throwaway entity's metadata, which an item stack does not
 * carry here. The two variants are separate blocks, though, so the stack's {@link BlockItem} names
 * the one it is - and {@link ExistenceBurnerBlock#dynamic()} is the same flag the block entity
 * reads. That is the only way this icon can show the dynamic burner's own bottom texture and tint,
 * rather than always showing the static one.</p>
 *
 * <p>What is <em>not</em> kept is upstream's framing: {@code (-0.5, -0.5, -0.5)} was tuned for
 * 1.7.10's inventory space, whose origin is the block's centre. 1.20.1's item space has the origin
 * at the block's minimum corner, so the machine is centred on it explicitly below. No scale is
 * applied: upstream had none, and the whole model is under one block tall.</p>
 */
public final class ExistenceBurnerItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The centre of the machine's bounding box in the space {@link ExistenceBurnerRenderer#draw}
     * leaves it in. The four boxes span {@code x} -4..4, {@code y} 0..14 and {@code z} -4..4 model
     * units, and that chain maps a model point to {@code (0.5 + x/16, y/16, 0.5 + z/16)}.
     */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 0.4375F;
    private static final float CENTRE_Z = 0.5F;

    private static ExistenceBurnerItemRenderer instance;

    private ExistenceBurnerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static ExistenceBurnerItemRenderer get() {
        if (instance == null) {
            instance = new ExistenceBurnerItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the icon box, shifting the machine's own centre onto the block centre.
        pose.translate(0.5F - CENTRE_X, 0.5F - CENTRE_Y, 0.5F - CENTRE_Z);
        ExistenceBurnerRenderer.draw(dynamic(stack), pose, buffers, light, overlay);
        pose.popPose();
    }

    /** The variant the stack names, defaulting to the static burner for anything unrecognised. */
    private static boolean dynamic(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ExistenceBurnerBlock burner
                && burner.dynamic();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return ExistenceBurnerItemRenderer.get();
        }
    }
}
