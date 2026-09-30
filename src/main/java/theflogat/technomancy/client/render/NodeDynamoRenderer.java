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
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;

/**
 * Draws the node dynamo, a port of {@code TileNodeDynamoRenderer} and {@code ModelNodeDynamo}.
 *
 * <p>The machine is a static twenty-two box frame: a base plate, four arms that fan out to a
 * small upper disc, four side panels and four angled posts. Nothing in the original moved the
 * geometry, so the model is baked once and drawn in the original declaration order - the order
 * {@code ModelBase.render()} used. Order is preserved because Techne had no depth sort, so two
 * boxes that share a face are decided by draw order, not by depth.</p>
 *
 * <p>The transform chain is the original's, unchanged: {@code scale(-1, -1, 1)} then
 * {@code translate(-.5F, -1.5F, .5F)}. A {@link PoseStack} already carries the block position, so
 * the original's leading {@code glTranslatef(x, y, z)} is the one call that is dropped. Under the
 * remaining chain a model point {@code (mx, my, mz)} in Techne units lands at block-local
 * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}; the two negative axes also keep the winding
 * order positive, so no culling has to be disabled.</p>
 *
 * <p>Upstream turned blending on with {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} before drawing, so
 * the sheet goes through {@link RenderType#entityTranslucent}. That reproduces the original's
 * blend exactly rather than substituting an alpha test that the original never asked for.</p>
 *
 * <p>The original additionally drew a Thaumcraft floaty line from the block's top centre to the
 * drained node while the tile was draining. That is not reproduced here: see the class javadoc of
 * {@link NodeDynamoBlockEntity}, whose beam state was never synced upstream and whose port draws
 * the trail as server-side dust particles instead, so there is no client state to read.</p>
 */
public final class NodeDynamoRenderer implements BlockEntityRenderer<NodeDynamoBlockEntity> {

    /** {@code Ref.MODEL_NODE_DYNAMO_TEXTURE}. 64x32, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/nodedynamo.png");

    /**
     * {@code ModelNodeDynamo}, box for box, in the original's render order. Every renderer carried
     * {@code mirror = true} and {@code setTextureSize(64, 32)}, so both are set once per part; the
     * offsets are the raw {@code addBox} arguments because each box sits at its rotation point.
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 32)
            .part("Base").uv(0, 8).mirror(true).at(0.0F, 20.0F, 0.0F)
                    .box(-8.0F, -4.0F, -8.0F, 16, 8, 16).end()
            .part("UpperBase").uv(24, 1).mirror(true).at(0.0F, 13.0F, 0.0F)
                    .rotate(0.0F, 0.7853982F, -3.141593F).box(-3.0F, -0.5F, -3.0F, 6, 1, 6).end()
            .part("SmallPost1").uv(4, 20).mirror(true).at(2.8F, 14.5F, 0.0F)
                    .rotate(0.0F, 0.7853982F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallPost2").uv(4, 20).mirror(true).at(0.0F, 14.5F, 2.8F)
                    .rotate(0.0F, 0.7853982F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallPost3").uv(4, 20).mirror(true).at(-2.8F, 14.5F, 0.0F)
                    .rotate(0.0F, 0.7853982F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallPost4").uv(4, 20).mirror(true).at(0.0F, 14.5F, -2.8F)
                    .rotate(0.0F, 0.7853982F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("Arm1").uv(0, 19).mirror(true).at(4.8F, 12.1F, 0.0F)
                    .rotate(0.0F, 0.0F, -2.007129F).box(-0.5F, -2.0F, -0.5F, 1, 4, 1).end()
            .part("Arm2").uv(0, 19).mirror(true).at(0.0F, 12.1F, 4.8F)
                    .rotate(2.007129F, 0.0F, 0.0F).box(-0.5F, -2.0F, -0.5F, 1, 4, 1).end()
            .part("Arm3").uv(0, 19).mirror(true).at(-4.8F, 12.1F, 0.0F)
                    .rotate(0.0F, 0.0F, 2.007129F).box(-0.5F, -2.0F, -0.5F, 1, 4, 1).end()
            .part("Arm4").uv(0, 19).mirror(true).at(0.0F, 12.1F, -4.8F)
                    .rotate(-2.007129F, 0.0F, 0.0F).box(-0.5F, -2.0F, -0.5F, 1, 4, 1).end()
            .part("SmallArm1").uv(0, 19).mirror(true).at(5.4F, 9.7F, 0.0F)
                    .rotate(0.0F, 0.0F, -0.4363323F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallArm2").uv(0, 19).mirror(true).at(0.0F, 9.7F, 5.4F)
                    .rotate(0.4363323F, 0.0F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallArm3").uv(0, 19).mirror(true).at(-5.4F, 9.7F, 0.0F)
                    .rotate(0.0F, 0.0F, 0.4363323F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("SmallArm4").uv(0, 19).mirror(true).at(0.0F, 9.7F, -5.4F)
                    .rotate(-0.4363323F, 0.0F, 0.0F).box(-0.5F, -1.5F, -0.5F, 1, 3, 1).end()
            .part("Pannel1").uv(54, 0).mirror(true).at(8.0F, 15.0F, 0.0F)
                    .rotate(0.0F, -3.141593F, 0.0F).box(-0.5F, -2.0F, -2.0F, 1, 11, 4).end()
            .part("Pannel2").uv(54, 0).mirror(true).at(0.0F, 15.0F, 8.0F)
                    .rotate(0.0F, 1.570796F, 0.0F).box(-0.5F, -2.0F, -2.0F, 1, 11, 4).end()
            .part("Pannel3").uv(54, 0).mirror(true).at(-8.0F, 15.0F, 0.0F)
                    .rotate(0.0F, 0.0F, 0.0F).box(-0.5F, -2.0F, -2.0F, 1, 11, 4).end()
            .part("Pannel4").uv(54, 0).mirror(true).at(0.0F, 15.0F, -8.0F)
                    .rotate(0.0F, -1.570796F, 0.0F).box(-0.5F, -2.0F, -2.0F, 1, 11, 4).end()
            .part("Post1").uv(0, 9).mirror(true).at(6.0F, 16.0F, 0.0F)
                    .rotate(2.356194F, -1.570796F, 0.0F).box(-1.0F, -1.0F, -2.0F, 2, 2, 5).end()
            .part("Post2").uv(0, 9).mirror(true).at(0.0F, 16.0F, 6.0F)
                    .rotate(0.7853982F, 0.0F, 0.0F).box(-1.0F, -1.0F, -2.0F, 2, 2, 5).end()
            .part("Post3").uv(0, 9).mirror(true).at(-6.0F, 16.0F, 0.0F)
                    .rotate(2.356194F, 1.570796F, 0.0F).box(-1.0F, -1.0F, -2.0F, 2, 2, 5).end()
            .part("Post4").uv(0, 9).mirror(true).at(0.0F, 16.0F, -6.0F)
                    .rotate(0.7853982F, 3.141593F, 0.0F).box(-1.0F, -1.0F, -2.0F, 2, 2, 5).end()
            .build();

    public NodeDynamoRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NodeDynamoBlockEntity machine, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileNodeDynamoRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockNodeDynamoRenderer.renderInventoryBlock} does nothing but set up a
     * transform and call {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileNodeDynamo}, so the item and the block are the same drawing by definition rather
     * than by two hand-kept copies.</p>
     *
     * <p>There is no per-tile argument: the model never moved and the original read no state to
     * draw it, which is why the inventory icon is the whole machine.</p>
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        // entityTranslucent, not entityCutout: the original explicitly enabled GL_BLEND with
        // SRC_ALPHA, ONE_MINUS_SRC_ALPHA. The two coincide on fully opaque texels, but translucent
        // is what the original asked for, so nothing is substituted.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);
        MODEL.render(pose, vertices, packedLight, packedOverlay);
        pose.popPose();
    }
}
