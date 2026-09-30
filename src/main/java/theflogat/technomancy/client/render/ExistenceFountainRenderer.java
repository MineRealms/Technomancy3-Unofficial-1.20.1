package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity;

/**
 * Draws the existence fountain's brim, a port of {@code ModelExistenceFountain.renderLiquid}.
 *
 * <p>The fountain's thirty-one body boxes are a plain JSON model; these nine are the ring of liquid
 * at the top, and they are here because the original tinted them with
 * {@code glColor4f(0, 0.2F, power / powerCap, 0.5F)} - a translucent wash whose blue channel is
 * how full the fountain is. A block model cannot compute that, so the ring is drawn over the JSON
 * body exactly as the original drew it over the model.</p>
 *
 * <p>Note that the original never moved the ring up or down, only recoloured it, so neither does
 * this. The height is the fountain's, not the fill's.</p>
 */
public final class ExistenceFountainRenderer implements BlockEntityRenderer<ExistenceFountainBlockEntity> {

    /** {@code Ref.MODEL_EXISTENCE_TEXTURE}. */
    private static final ResourceLocation LIQUID =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/existence.png");

    /**
     * The original's {@code liquid} list: nine boxes forming the top rim, all at {@code texOffs}
     * (0, 0) on a 64x32 sheet and all sharing the model's own rotation point of zero. These are
     * the raw {@code addBox} arguments; the renderer's
     * {@code scale(-1,-1,1); translate(.5,-.5,-.5)} is applied to the pose below, not baked in.
     */
    private static final TechneModel RING = TechneModel.builder(64, 32)
            .part("ring").uv(0, 0).at(0.0F, 0.0F, 0.0F)
                    .box(-20.0F, -8.0F, 12.0F, 8, 1, 8)
                    .box(-19.0F, -8.0F, 11.0F, 6, 1, 1)
                    .box(-19.0F, -8.0F, 20.0F, 6, 1, 1)
                    .box(-12.0F, -8.0F, 13.0F, 1, 1, 6)
                    .box(-21.0F, -8.0F, 13.0F, 1, 1, 6)
                    .box(-17.0F, -8.0F, 10.0F, 2, 1, 1)
                    .box(-17.0F, -8.0F, 21.0F, 2, 1, 1)
                    .box(-11.0F, -8.0F, 15.0F, 1, 1, 2)
                    .box(-22.0F, -8.0F, 15.0F, 1, 1, 2)
                    .end()
            .build();

    public ExistenceFountainRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ExistenceFountainBlockEntity fountain, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int capacity = fountain.powerCap();
        float fill = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) fountain.power() / capacity);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(LIQUID));
        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.5F, -0.5F, -0.5F);
        RING.render(pose, vertices, packedLight, packedOverlay, 0.0F, 0.2F, fill, 0.5F);
        pose.popPose();
    }
}
