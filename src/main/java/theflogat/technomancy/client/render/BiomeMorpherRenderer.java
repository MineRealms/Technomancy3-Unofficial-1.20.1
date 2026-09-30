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
import theflogat.technomancy.common.tiles.machines.BiomeMorpherBlockEntity;

/**
 * Draws the biome morpher, a port of {@code TileBiomeMorpherRenderer} and
 * {@code ModelBiomeMorpher}.
 *
 * <p>A renderer is unavoidable here, and upstream reached the same conclusion:
 * {@code BlockBiomeMorpherRenderer.renderWorldBlock} returns {@code false}, so every pixel of the
 * block comes from the special renderer. Two features of the model also put it outside what a
 * block model JSON can say. The four glass panes are zero-width boxes - a box with no thickness
 * cannot be an element - and they carry {@code texOffs(30, -14)}, a negative {@code v} that runs
 * off the top of the 32 pixel sheet and only works because the sampler repeats, so the band lands
 * on the sheet's bottom rows. A block model's uv is clamped to its sprite.</p>
 *
 * <p>The machine has no moving parts: the original bound the texture once, turned blending on and
 * drew the whole model in one pass, reading nothing from the tile entity. This renderer therefore
 * reads nothing from {@link BiomeMorpherBlockEntity} either, and there is no state to thread
 * through {@link #draw}.</p>
 *
 * <p>The transform chain is the original's, in the original's order, because a {@code PoseStack}
 * applies a call to the vertices in the same order OpenGL did:</p>
 * <pre>
 *   glScalef(-1, -1, 1);
 *   glTranslatef(-.5F, -1.5F, .5F);
 * </pre>
 *
 * <p>That chain sends a model point {@code (mx, my, mz)} to block-local
 * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}. The {@code Bottom} plate, whose box spans
 * {@code my} 23..24 and {@code mx} -8..8, therefore lands at block {@code y} 0..1/16 and
 * {@code x} 0..1 - the plate the machine stands on. Unlike the node fabricator and the flux lamp
 * the original adds no {@code rotateY} here, and none is added.</p>
 *
 * <p>The two negative axes keep the winding order positive, so nothing needs culling disabled. The
 * zero-width glass panes still emit a pair of coincident faces at their plane; culling drops the
 * one that faces away, leaving the single visible sheet the original drew.</p>
 */
public final class BiomeMorpherRenderer implements BlockEntityRenderer<BiomeMorpherBlockEntity> {

    /** {@code Ref.MODEL_BIOME_MODIFIER_TEXTURE}. 64x32, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/biomemorpher.png");

    /**
     * {@code ModelBiomeMorpher}, box for box. Fourteen renderers, one box each, all with
     * {@code mirror = true} and {@code setTextureSize(64, 32)}.
     *
     * <p>The eight {@code CenterTop*} / {@code CenterBottom*} renderers the original declared are
     * omitted: they are commented out of both the constructor and {@code render()}, so they were
     * never built and never drawn.</p>
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 32)
            .part("Bottom").uv(0, 15).mirror(true).at(0.0F, 25.0F, 0.0F)
                    .box(-8.0F, -2.0F, -8.0F, 16, 1, 16).end()
            .part("Top").uv(0, 15).mirror(true).at(0.0F, 8.0F, 0.0F).rotate(0.0F, 0.0F, 3.141593F)
                    .box(-8.0F, 0.0F, -8.0F, 16, 1, 16).end()
            .part("Post1").uv(0, 14).mirror(true).at(-8.0F, 16.0F, 7.0F)
                    .box(0.0F, -8.0F, 0.0F, 1, 16, 1).end()
            .part("Post2").uv(0, 14).mirror(true).at(-8.0F, 16.0F, -8.0F)
                    .box(0.0F, -8.0F, 0.0F, 1, 16, 1).end()
            .part("Post3").uv(0, 14).mirror(true).at(7.0F, 15.0F, -8.0F)
                    .box(0.0F, -8.0F, 0.0F, 1, 16, 1).end()
            .part("Post4").uv(0, 14).mirror(true).at(7.0F, 15.0F, 7.0F)
                    .box(0.0F, -8.0F, 0.0F, 1, 16, 1).end()
            .part("Side1").uv(16, 0).mirror(true).at(-8.0F, 16.0F, 0.0F)
                    .box(0.0F, -2.0F, -2.0F, 1, 4, 4).end()
            .part("Side2").uv(16, 0).mirror(true).at(0.0F, 16.0F, -8.0F)
                    .rotate(0.0F, -1.570796F, 0.0F).box(0.0F, -2.0F, -2.0F, 1, 4, 4).end()
            .part("Side3").uv(16, 0).mirror(true).at(7.0F, 16.0F, 0.0F)
                    .box(0.0F, -2.0F, -2.0F, 1, 4, 4).end()
            .part("Side4").uv(16, 0).mirror(true).at(0.0F, 16.0F, 8.0F)
                    .rotate(0.0F, 1.570796F, 0.0F).box(0.0F, -2.0F, -2.0F, 1, 4, 4).end()
            .part("Glass1").uv(30, -14).mirror(true).at(-8.0F, 15.0F, 0.0F)
                    .box(0.5F, -7.0F, -7.0F, 0, 15, 14).end()
            .part("Glass2").uv(30, -14).mirror(true).at(0.0F, 15.0F, -7.0F)
                    .rotate(0.0F, -1.570796F, 0.0F).box(-0.5F, -7.0F, -7.0F, 0, 15, 14).end()
            .part("Glass3").uv(30, -14).mirror(true).at(7.0F, 15.0F, 0.0F)
                    .rotate(0.0F, 3.141593F, 0.0F).box(-0.5F, -7.0F, -7.0F, 0, 15, 14).end()
            .part("Glass4").uv(30, -14).mirror(true).at(0.0F, 15.0F, 8.0F)
                    .rotate(0.0F, 1.570796F, 0.0F).box(0.5F, -7.0F, -7.0F, 0, 15, 14).end()
            .build();

    public BiomeMorpherRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BiomeMorpherBlockEntity machine, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileBiomeMorpherRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockBiomeMorpherRenderer.renderInventoryBlock} does nothing but set up
     * a transform and call {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileBiomeMorpher}, so the item and the block are the same drawing by definition rather
     * than by two hand-kept copies.</p>
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        // The original's glEnable(GL_BLEND) plus glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) wrapped
        // the entire model, not just the glass, so the whole thing is drawn through one blended
        // buffer. entityTranslucent is that blend: the glass panes' alpha fades the frame behind
        // them, and the solid plates are opaque texels that come out unchanged. entityCutout would
        // be wrong here - the glass texels are partially transparent rather than fully discarded.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);

        // glColor4f(1, 1, 1, 1) upstream was a no-op, so no colour overload is used. The original
        // had no depth sort, so the declaration order below is the render order.
        MODEL.render(pose, vertices, packedLight, packedOverlay);
        pose.popPose();
    }
}
