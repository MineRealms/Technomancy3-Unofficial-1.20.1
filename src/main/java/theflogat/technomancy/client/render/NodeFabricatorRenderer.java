package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity;

/**
 * Draws the node fabricator, a port of {@code TileNodeGeneratorRenderer} and
 * {@code ModelNodeGenerator}.
 *
 * <p>A renderer is unavoidable here, for four reasons that a JSON model cannot express: the core
 * plate spins around X at a speed the ritual drives, the core carries its own 45 degree rotation,
 * the boards and the core are alpha-blended, and the boards are two and a bit blocks across, which
 * no block model should be. Upstream reached the same conclusion - {@code BlockNodeGeneratorRenderer.renderWorldBlock}
 * returns {@code false} and every pixel of the block comes from the special renderer.</p>
 *
 * <p>The whole transform chain is the original's, in the original's order, because
 * {@code PoseStack} applies a call to the vertices in the same order OpenGL did:</p>
 * <pre>
 *   glScalef(-1, -1, 1);
 *   glTranslatef(-.5F, -1.5F, .5F);
 *   glRotatef(-90, 0, 1, 0);
 *   switch (facing) { 3: 180, 4: 270, 5: 90 }   // 1.7.10 ForgeDirection, not 1.20 Direction
 * </pre>
 *
 * <p>The two negative axes keep the winding order positive, so nothing needs culling disabled.</p>
 */
public final class NodeFabricatorRenderer implements BlockEntityRenderer<NodeFabricatorBlockEntity> {

    /** {@code Ref.MODEL_NODE_GENERATOR_TEXTURE}. 256x128, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/nodegenerator.png");

    private static final String CORE = "CenterPiece1";

    /**
     * {@code ModelNodeGenerator}, box for box. Every box sits at its rotation point, which is why
     * the offsets are the raw {@code addBox} arguments; all nine renderers carried
     * {@code mirror = true} and {@code setTextureSize(256, 128)}.
     */
    private static final TechneModel MODEL = TechneModel.builder(256, 128)
            .part("OuterBoard").uv(176, 52).mirror(true).at(-8.0F, 0.0F, 0.0F)
                    .box(0.0F, -19.0F, -19.0F, 2, 38, 38).end()
            .part("MiddleBoard").uv(0, 40).mirror(true).at(-6.0F, 0.0F, 0.0F)
                    .box(0.0F, -22.0F, -22.0F, 2, 44, 44).end()
            .part("BackBoard").uv(92, 52).mirror(true).at(-4.0F, 0.0F, 0.0F)
                    .box(0.0F, -19.0F, -19.0F, 4, 38, 38).end()
            .part("Base").uv(200, 0).mirror(true).at(2.0F, 0.0F, 0.0F)
                    .box(-6.0F, -8.0F, -8.0F, 12, 32, 16).end()
            .part("Pylon4").uv(0, 0).mirror(true).at(-12.0F, -16.0F, 16.0F)
                    .box(-4.0F, -2.0F, -2.0F, 8, 4, 4).end()
            .part("Pylon3").uv(0, 0).mirror(true).at(-12.0F, 16.0F, 16.0F)
                    .box(-4.0F, -2.0F, -2.0F, 8, 4, 4).end()
            .part("Pylon1").uv(0, 0).mirror(true).at(-12.0F, -16.0F, -16.0F)
                    .box(-4.0F, -2.0F, -2.0F, 8, 4, 4).end()
            .part("Pylon2").uv(0, 0).mirror(true).at(-12.0F, 16.0F, -16.0F)
                    .box(-4.0F, -2.0F, -2.0F, 8, 4, 4).end()
            .part(CORE).uv(144, 0).mirror(true).at(-7.5F, 0.0F, 0.0F).rotate(0.7853982F, 0.0F, 0.0F)
                    .box(-1.0F, -13.0F, -13.0F, 2, 26, 26).end()
            .build();

    public NodeFabricatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NodeFabricatorBlockEntity machine, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(machine.facing(), machine.spin(partialTick), pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileNodeGeneratorRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockNodeGeneratorRenderer.renderInventoryBlock} does nothing but set up
     * a transform and call {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileNodeGenerator}, so the item and the block are the same drawing by definition
     * rather than by two hand-kept copies.</p>
     *
     * @param spin the core's rotation about X in degrees; the original read
     *     {@code TileNodeGenerator.rotation}, which is 0 for an inventory render.
     */
    public static void draw(Direction facing, float spin, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        VertexConsumer core = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        VertexConsumer boards = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        applyFacing(pose, facing);

        // The original drew the core first, unblended, then turned blending on for the boards.
        // The core's sheet is mostly transparent, so it goes through cutout rather than
        // translucent: alpha test discards those texels instead of letting them darken the boards
        // behind them.
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        MODEL.render(CORE, pose, core, packedLight, packedOverlay);
        pose.popPose();

        MODEL.renderExcept(pose, boards, packedLight, packedOverlay, CORE);
        pose.popPose();
    }

    /** The original's {@code switch (facing)} over 1.7.10 {@code ForgeDirection} ordinals. */
    private static void applyFacing(PoseStack pose, Direction facing) {
        switch (facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(270.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            default -> {
                // NORTH is the model's own orientation.
            }
        }
    }
}
