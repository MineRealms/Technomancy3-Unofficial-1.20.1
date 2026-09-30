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
import theflogat.technomancy.common.tiles.technom.existence.ExistenceBurnerBlockEntity;

/**
 * Draws the existence burner, a port of {@code TileExistenceBurnerRenderer} and
 * {@code ModelExistenceBurner}.
 *
 * <p>The burner is four boxes: a narrow post, a plate on top of it, a plate under it, and the green
 * cube the machine burns inside. Only three things change between frames, and none of them is
 * motion: which texture the bottom plate uses, and which green the cube is tinted - both keyed off
 * the variant. The original read that variant from {@code te.getBlockMetadata() == 1}; this port
 * has one block entity behind both blocks, so the flag comes from
 * {@link ExistenceBurnerBlockEntity#dynamic()}, which is set from the block at construction.</p>
 *
 * <p>The transform is the original's own, and it is <em>not</em> the usual
 * {@code scale(-1,-1,1); translate(-.5,-1.5,.5)} chain: {@code TileExistenceBurnerRenderer} opens
 * with a bare {@code glTranslatef(.5F, 0, .5F)} and no scale. That is kept verbatim. The block
 * position is factored out because a {@link PoseStack} already carries it, so a model point
 * {@code (mx, my, mz)} in Techne units lands at block-local
 * {@code (0.5 + mx/16, my/16, 0.5 + mz/16)} - which puts the four boxes in {@code x} 0.25..0.75,
 * {@code y} 0..0.875, {@code z} 0.25..0.75, exactly where the original put them.</p>
 */
public final class ExistenceBurnerRenderer implements BlockEntityRenderer<ExistenceBurnerBlockEntity> {

    /** {@code Ref.MODEL_ANVIL_TEXTURE}. 64x32, so it is a model sheet, not a sprite. */
    private static final ResourceLocation ANVIL =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/anvil.png");

    /** {@code Ref.MODEL_BRF_TEXTURE}. The original lower-cases the file name to {@code bottomrf.png}. */
    private static final ResourceLocation BOTTOM =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/bottomrf.png");

    /** {@code Ref.MODEL_WHITE_TEXTURE}, the sheet the tinted cube is cut from. */
    private static final ResourceLocation WHITE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/white.png");

    private static final String POST = "Post";
    private static final String TOP = "Top";
    private static final String BOTTOM_PLATE = "Bottom";
    private static final String CUBE = "Cube";

    /**
     * {@code ModelExistenceBurner}, box for box. Every renderer was built with
     * {@code new ModelRenderer(this, 0, 0)} - {@code texOffs (0, 0)}, {@code mirror = false}, no
     * {@code setRotationPoint} and no rotation - on a sheet that never called
     * {@code setTextureSize}, so the {@code ModelBase} defaults of 64x32 apply.
     *
     * <p>Declaration order is the original's render order: {@code render()} walked the {@code core}
     * list (post, then top plate), then {@code renderBottom()} drew the bottom plate, then
     * {@code renderCube()} the cube. Techne had no depth sort, so this order is what decides which
     * face wins where the plates meet the post.</p>
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 32)
            .part(POST).uv(0, 0).mirror(false).at(0.0F, 0.0F, 0.0F)
                    .box(-1.0F, 1.0F, -1.0F, 2, 6, 2).end()
            .part(TOP).uv(0, 0).mirror(false).at(0.0F, 0.0F, 0.0F)
                    .box(-4.0F, 7.0F, -4.0F, 8, 1, 8).end()
            .part(BOTTOM_PLATE).uv(0, 0).mirror(false).at(0.0F, 0.0F, 0.0F)
                    .box(-4.0F, 0.0F, -4.0F, 8, 1, 8).end()
            .part(CUBE).uv(0, 0).mirror(false).at(0.0F, 0.0F, 0.0F)
                    .box(-2.0F, 10.0F, -2.0F, 4, 4, 4).end()
            .build();

    public ExistenceBurnerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ExistenceBurnerBlockEntity burner, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(burner.dynamic(), pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileExistenceBurnerRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockExistenceBurnerRenderer.renderInventoryBlock} only set up a
     * transform and called {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileExistenceBurner} or {@code TileExistenceDynamicBurner} whose metadata selected the
     * variant, so the item and the block were the same drawing by definition.</p>
     *
     * @param dynamic the dynamic variant, the original's {@code getBlockMetadata() == 1}: it swaps
     *     the bottom plate's texture and lightens the cube's tint
     */
    public static void draw(boolean dynamic, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        // One consumer per RenderType, requested in the original's binding order so the batched
        // draws flush in that same order. The original never enabled blending - it relied on the
        // alpha test that 1.7.10 leaves on for tile entities - so cutout is the match: it discards
        // the sheet's transparent texels instead of blending them.
        VertexConsumer anvil = buffers.getBuffer(RenderType.entityCutout(ANVIL));
        VertexConsumer bottomSheet = buffers.getBuffer(RenderType.entityCutout(BOTTOM));
        VertexConsumer white = buffers.getBuffer(RenderType.entityCutout(WHITE));

        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);

        // bindTexture(modelTexture); model.render()
        MODEL.render(POST, pose, anvil, packedLight, packedOverlay);
        MODEL.render(TOP, pose, anvil, packedLight, packedOverlay);

        // if (meta == 1) bindTexture(bottom); model.renderBottom(). The original only rebound the
        // texture for the dynamic variant, so the static burner's bottom plate is drawn on the
        // anvil sheet the core used.
        MODEL.render(BOTTOM_PLATE, pose, dynamic ? bottomSheet : anvil, packedLight, packedOverlay);

        // bindTexture(cube); model.renderCube(meta == 1). The original tinted with
        // glColor3f(burn ? 0.5F : 0, 0.7F, 0) and then reset to white, so only the cube is tinted.
        MODEL.render(CUBE, pose, white, packedLight, packedOverlay,
                dynamic ? 0.5F : 0.0F, 0.7F, 0.0F, 1.0F);

        pose.popPose();
    }
}
