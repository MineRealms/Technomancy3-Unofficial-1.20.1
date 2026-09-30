package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws the eldritch consumer as an item, the way
 * {@code BlockEldritchConsumerRenderer.renderInventoryBlock} drew it in 1.7.10: by handing the
 * whole job to the block's own renderer.
 *
 * <p>Upstream's inventory path was {@code translate(-0.5, -0.5, -0.5)} followed by a call to
 * {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
 * {@code TileEldritchConsumer}, so the item and the block were the same drawing by construction
 * rather than by two hand-kept copies. A hand-written JSON model cannot stand in for it: the
 * twenty arm segments and the rotor move, and the machine is a special-rendered block whose JSON
 * is an empty shell. What is not kept is upstream's framing constant, which was tuned for 1.7.10's
 * inventory space; the block-space centre below re-derives it for 1.20.1, where the item's block
 * space is the block's own {@code [0, 1]} cube with the origin at its minimum corner.</p>
 *
 * <p>The model already spans {@code x} and {@code z} exactly {@code 0..1} and rests on
 * {@code y = 0} - the short transform chain in {@link EldritchConsumerRenderer#draw} is what puts
 * it there - so the only correction is the 0.029 lift that centres its 0..0.9415 height in the
 * cube. The item's {@code display} block still supplies the presentation rotation, so the icon
 * sits at the same angle as every other block item.</p>
 */
public final class EldritchConsumerItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The centre of the machine's bounding box in the space {@link EldritchConsumerRenderer#draw}
     * leaves it in. The chain there is {@code (-mx/16, -my/16, mz/16)}, the thirty boxes span
     * model {@code x} -16..0, {@code y} -15.064..0 and {@code z} 0..16, and the rotated fins stay
     * inside that, giving block space {@code x} 0..1, {@code y} 0..0.9415, {@code z} 0..1.
     */
    private static final float CENTRE_X = 0.5F;
    private static final float CENTRE_Y = 0.47075112F;
    private static final float CENTRE_Z = 0.5F;

    private static EldritchConsumerItemRenderer instance;

    private EldritchConsumerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static EldritchConsumerItemRenderer get() {
        if (instance == null) {
            instance = new EldritchConsumerItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Block space -> the block's own cube: move the model's centre onto (0.5, 0.5, 0.5), which
        // is what upstream's translate(-.5, -.5, -.5) did in 1.7.10's differently centred space.
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.translate(-CENTRE_X, -CENTRE_Y, -CENTRE_Z);
        // A fresh TileEldritchConsumer: panelRotation 0 and cooldown 0, so the arms rest and the
        // rotor does not turn, exactly as the original inventory render showed it.
        EldritchConsumerRenderer.draw(0.0F, false, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return EldritchConsumerItemRenderer.get();
        }
    }
}
