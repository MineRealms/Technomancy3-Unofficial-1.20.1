package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import theflogat.technomancy.common.blocks.technom.CrystalBlock;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * Draws a crystal as an item, the way {@code BlockCrystalRenderer.renderInventoryBlock} drew it in
 * 1.7.10: by handing the whole job to the block's own renderer.
 *
 * <p>Upstream's inventory path was {@code translate(-0.5, -0.5, -0.5)} followed by
 * {@code renderTileEntityAt} on a throwaway {@code TileCrystal}, so the item and the block were the
 * same drawing by construction. That translate is what the 1.20.1 item space already does for us:
 * {@link CrystalRenderer#draw} leaves the model in block space {@code [0,1]^3}, and the item's
 * {@code display} block in the model JSON supplies the presentation rotation, exactly as it does
 * for every other block item. Nothing else is needed - no centring, no rescale - because the
 * largest crystal box is the block.</p>
 *
 * <p>The stack's own block decides the tint. A fresh {@code TileCrystal} had stage 0, so the item
 * is drawn at stage 0 as well.</p>
 */
public final class CrystalItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static CrystalItemRenderer instance;

    private CrystalItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Built on first use: the super constructor needs a live client. */
    public static CrystalItemRenderer get() {
        if (instance == null) {
            instance = new CrystalItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Ritual.Type type = stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof CrystalBlock crystal ? crystal.type() : Ritual.Type.EARTH;
        CrystalRenderer.draw(0, type, pose, buffers, light, overlay);
    }

    /** Wires the item to {@link #get()}; registered from {@code TechnomancyClient}. */
    public static final class Extensions implements IClientItemExtensions {

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return CrystalItemRenderer.get();
        }
    }
}
