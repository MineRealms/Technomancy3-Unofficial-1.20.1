package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity;

/**
 * Draws the advanced deconstruction table, a port of {@code TileAdvDeconTableRenderer} and
 * {@code ModelAdvDeconTable}.
 *
 * <p>The table itself is nine static boxes - a base, four corner posts and a three-layer top - so
 * the whole reason this cannot be a block model is the second half of the original renderer: a copy
 * of the object in the machine's slot is drawn floating above the table, turning slowly. The
 * animation state is therefore the slot's contents, read straight off the block entity.</p>
 *
 * <p>The transform is the original's, in the original's order. {@code PoseStack} applies a call to
 * the vertices in the same order OpenGL did, and the block position is factored out because the
 * pose already carries it:</p>
 * <pre>
 *   glScalef(-1, -1, 1);
 *   glTranslatef(-.5F, -1.5F, .5F);
 * </pre>
 * <p>Under that chain a model point {@code (mx, my, mz)} in Techne units lands at block-local
 * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}. The nine boxes then span {@code x} 0..1,
 * {@code y} 0..15/16 and {@code z} 0..1: the base sits on the floor and the top plate stops one
 * sixteenth short of the ceiling, exactly as the original's did.</p>
 *
 * <p>The floating item is a separate push/pop in the original, so it does <em>not</em> inherit the
 * {@code scale(-1, -1, 1)} chain - it is placed in ordinary world axes, spun about Y. Upstream drew
 * a ghost {@code EntityItem} with bobbing switched off; this calls
 * {@code ItemRenderer.renderStatic} with the same {@link ItemDisplayContext#GROUND} the modern
 * dropped-item renderer uses, which is the same drawing without an entity to allocate.</p>
 */
public final class AdvDeconTableRenderer implements BlockEntityRenderer<AdvDeconTableBlockEntity> {

    /** {@code Ref.MODEL_RECONSTRUCTOR_TEXTURE}. 64x32, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/reconstructor.png");

    /**
     * {@code ModelAdvDeconTable}, box for box. Every renderer was built with
     * {@code setTextureSize(64, 32)} and {@code mirror = true}, so those are stated once per part.
     *
     * <p>Declaration order is the original's {@code render()} order - base, both base plates, the
     * four posts, then the top - and it is load-bearing: Techne had no depth sort, so where two
     * boxes share a face the later one wins.</p>
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 32)
            .part("Base").uv(0, 0).mirror(true).at(0.0F, 22.0F, 0.0F)
                    .box(-8.0F, 0.0F, -8.0F, 16, 2, 16).end()
            .part("Base2").uv(0, 18).mirror(true).at(0.0F, 22.0F, 0.0F).rotate(0.0F, 0.0F, 3.141593F)
                    .box(-4.0F, 0.0F, -4.0F, 8, 2, 8).end()
            .part("Post1").uv(0, 0).mirror(true).at(7.0F, 12.0F, 7.0F).rotate(0.0F, 3.141593F, 0.0F)
                    .box(-1.0F, 0.0F, -1.0F, 2, 10, 2).end()
            .part("Post2").uv(0, 0).mirror(true).at(-7.0F, 12.0F, 7.0F).rotate(0.0F, 1.570796F, 0.0F)
                    .box(-1.0F, 0.0F, -1.0F, 2, 10, 2).end()
            .part("Post3").uv(0, 0).mirror(true).at(7.0F, 12.0F, -7.0F).rotate(0.0F, -1.570796F, 0.0F)
                    .box(-1.0F, 0.0F, -1.0F, 2, 10, 2).end()
            .part("Post4").uv(0, 0).mirror(true).at(-7.0F, 12.0F, -7.0F)
                    .box(-1.0F, 0.0F, -1.0F, 2, 10, 2).end()
            .part("TopBase").uv(0, 0).mirror(true).at(0.0F, 10.0F, 0.0F).rotate(0.0F, 1.570796F, 0.0F)
                    .box(-8.0F, 0.0F, -8.0F, 16, 2, 16).end()
            .part("TopBase2").uv(0, 18).mirror(true).at(0.0F, 12.0F, 0.0F)
                    .box(-4.0F, 0.0F, -4.0F, 8, 2, 8).end()
            .part("TopBase3").uv(0, 18).mirror(true).at(0.0F, 9.0F, 0.0F)
                    .box(-4.0F, 0.0F, -4.0F, 8, 1, 8).end()
            .build();

    public AdvDeconTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AdvDeconTableBlockEntity table, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(table.items().getStackInSlot(AdvDeconTableBlockEntity.SLOT), ticks(partialTick),
                table.getLevel(), pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileAdvDeconTableRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockAdvDeconTableRenderer.renderInventoryBlock} only set up a transform
     * and called {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileAdvDeconTable}, so the item and the block were the same drawing by definition.</p>
     *
     * @param stack the object in slot 0, or {@link ItemStack#EMPTY} to draw the bare table; the
     *     original skipped the floating copy when the slot was empty
     * @param ticks the original's {@code renderViewEntity.ticksExisted + partialTick}; the spin is
     *     {@code ticks % 360} degrees about Y
     * @param level the world, only used to resolve the floating item's model, or {@code null}
     */
    public static void draw(ItemStack stack, float ticks, @Nullable Level level, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // The original enabled GL_BLEND with the standard alpha function and never enabled the
        // alpha test, so blending is what it actually wanted. The sheet has antialiased edges (16
        // distinct alpha values), which an alpha test would flatten to opaque, so translucent is
        // the faithful match rather than cutout.
        VertexConsumer model = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);
        MODEL.render(pose, model, packedLight, packedOverlay);
        pose.popPose();

        if (stack.isEmpty()) {
            return;
        }

        // The original's second block: a ghost EntityItem with hoverStart = 0 (bob off) and, for a
        // block item, a slightly higher and larger copy. It was drawn with GL_LIGHTING disabled, so
        // the item is full-bright here too.
        boolean blockItem = stack.getItem() instanceof BlockItem;
        float yOffset = blockItem ? 0.4F : 0.3F;
        float scale = blockItem ? 0.9F : 0.7F;

        pose.pushPose();
        pose.translate(0.5F, yOffset, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(ticks % 360.0F));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                LightTexture.FULL_BRIGHT, packedOverlay, pose, buffers, level, 0);
        pose.popPose();
    }

    /** The original's {@code renderViewEntity.ticksExisted + partialTick}. */
    private static float ticks(float partialTick) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return (camera == null ? 0.0F : camera.tickCount) + partialTick;
    }
}
