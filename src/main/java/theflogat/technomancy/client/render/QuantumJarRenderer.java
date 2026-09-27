package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import theflogat.technomancy.common.blocks.essentia.QuantumJarBlock;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;

/**
 * Draws the two parts of a quantum jar that a static model cannot: the essentia level and the
 * label.
 *
 * <p>The jar shell itself stays a JSON model, so item, hand and inventory rendering are
 * correct without any work here. The geometry and light handling follow TC4R's
 * {@code WardedJarRenderer} so a quantum jar reads like a warded jar at a glance.</p>
 */
public final class QuantumJarRenderer implements BlockEntityRenderer<QuantumJarBlockEntity> {

    /** TC4R's own liquid sprite, so the two jars animate identically. */
    private static final ResourceLocation LIQUID = new ResourceLocation("thaumcraft", "block/animatedglow");
    private static final ResourceLocation LABEL = new ResourceLocation("thaumcraft", "textures/models/label.png");

    // The shell is 3/16..13/16 wide and 12/16 tall; the essentia sits inside it.
    private static final float LIQUID_MIN = 0.25F;
    private static final float LIQUID_MAX = 0.75F;
    private static final float LIQUID_FLOOR = 0.0625F;
    private static final float LIQUID_RANGE = 0.625F;

    private static final float LABEL_CENTER_Y = 0.41F;
    private static final float LABEL_HALF = 0.25F;
    private static final float LABEL_ICON_HALF = 0.168F;
    /** Just off the shell face, label behind icon, so neither z-fights the model. */
    private static final float LABEL_PLANE = 0.187F;
    private static final float ICON_PLANE = 0.186F;

    public QuantumJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuantumJarBlockEntity jar, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        AspectId aspect = jar.aspect();
        if (aspect != null && jar.amount() > 0) {
            renderEssentia(aspect, jar.amount(), poseStack, buffer, packedLight);
        }
        AspectId filter = jar.filter();
        if (filter != null) {
            Direction facing = jar.getBlockState().getValue(QuantumJarBlock.FACING);
            renderLabel(filter, facing, poseStack, buffer);
        }
    }

    private static void renderEssentia(AspectId aspect, int amount, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        /*
         * The height is a float fraction of the capacity. The original computed
         * (abs(amount / 5) / abs(maxAmount / 5)) * 0.65f entirely in integers, so it only ever
         * produced 0 or 0.65 and the level never moved with the contents (defect A-4).
         */
        renderColumn(aspect, (float) amount / QuantumJarBlockEntity.CAPACITY, poseStack, buffer, packedLight);
    }

    /**
     * Draws an essentia column of the given fill fraction inside a jar-shaped shell; shared with
     * the creative jar, which has the same shell.
     */
    public static void renderColumn(AspectId aspect, float fill, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        AspectDefinition definition = AspectApi.get(aspect);
        if (definition == null) {
            return;
        }
        float top = LIQUID_FLOOR + Math.max(0.0F, Math.min(1.0F, fill)) * LIQUID_RANGE;
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LIQUID);
        int color = definition.color();
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        // Essentia glows faintly even in an enclosed jar, as it does in Thaumcraft.
        int light = Math.max(200, packedLight);
        /*
         * The interior is opaque and goes on the depth-writing solid target, so the
         * translucent shell blends over it instead of the two being sorted against each other.
         */
        VertexConsumer vertices = buffer.getBuffer(RenderType.solid());
        PoseStack.Pose pose = poseStack.last();
        face(vertices, pose, sprite, red, green, blue, light, Direction.DOWN, top);
        face(vertices, pose, sprite, red, green, blue, light, Direction.UP, top);
        face(vertices, pose, sprite, red, green, blue, light, Direction.NORTH, top);
        face(vertices, pose, sprite, red, green, blue, light, Direction.SOUTH, top);
        face(vertices, pose, sprite, red, green, blue, light, Direction.WEST, top);
        face(vertices, pose, sprite, red, green, blue, light, Direction.EAST, top);
    }

    /** One face of the essentia column, wound counter-clockwise when seen from outside. */
    private static void face(VertexConsumer vertices, PoseStack.Pose pose, TextureAtlasSprite sprite,
            int red, int green, int blue, int light, Direction facing, float top) {
        float lo = LIQUID_MIN;
        float hi = LIQUID_MAX;
        float bottom = LIQUID_FLOOR;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        switch (facing) {
            case DOWN -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        lo, bottom, lo, hi, bottom, lo, hi, bottom, hi, lo, bottom, hi);
            }
            case UP -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        lo, top, hi, hi, top, hi, hi, top, lo, lo, top, lo);
            }
            case NORTH -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        lo, bottom, lo, lo, top, lo, hi, top, lo, hi, bottom, lo);
            }
            case SOUTH -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        hi, bottom, hi, hi, top, hi, lo, top, hi, lo, bottom, hi);
            }
            case WEST -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        lo, bottom, hi, lo, top, hi, lo, top, lo, lo, bottom, lo);
            }
            case EAST -> {
                quad(vertices, pose, red, green, blue, light, facing, u0, u1, v0, v1,
                        hi, bottom, lo, hi, top, lo, hi, top, hi, hi, bottom, hi);
            }
        }
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, int red, int green, int blue,
            int light, Direction normal, float u0, float u1, float v0, float v1,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(vertices, pose, x1, y1, z1, red, green, blue, 255, u0, v1, light, normal);
        vertex(vertices, pose, x2, y2, z2, red, green, blue, 255, u0, v0, light, normal);
        vertex(vertices, pose, x3, y3, z3, red, green, blue, 255, u1, v0, light, normal);
        vertex(vertices, pose, x4, y4, z4, red, green, blue, 255, u1, v1, light, normal);
    }

    private static void renderLabel(AspectId filter, Direction facing, PoseStack poseStack,
            MultiBufferSource buffer) {
        AspectDefinition definition = AspectApi.get(filter);
        if (definition == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        // The paper label, then the aspect icon just in front of it.
        labelQuad(buffer.getBuffer(RenderType.entityCutoutNoCull(LABEL)), pose, facing,
                0.5F - LABEL_HALF, 0.5F + LABEL_HALF,
                LABEL_CENTER_Y - LABEL_HALF, LABEL_CENTER_Y + LABEL_HALF,
                LABEL_PLANE, 0xFFFFFF);
        labelQuad(buffer.getBuffer(RenderType.entityCutoutNoCull(definition.texture())), pose, facing,
                0.5F - LABEL_ICON_HALF, 0.5F + LABEL_ICON_HALF,
                LABEL_CENTER_Y - LABEL_ICON_HALF, LABEL_CENTER_Y + LABEL_ICON_HALF,
                ICON_PLANE, definition.color());
    }

    /** A flat quad on one horizontal face, at {@code plane} depth from that face. */
    private static void labelQuad(VertexConsumer vertices, PoseStack.Pose pose, Direction facing,
            float min, float max, float minY, float maxY, float plane, int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int light = LightTexture.FULL_BRIGHT;
        switch (facing) {
            case NORTH -> {
                vertex(vertices, pose, min, minY, plane, red, green, blue, 255, 0, 1, light, facing);
                vertex(vertices, pose, min, maxY, plane, red, green, blue, 255, 0, 0, light, facing);
                vertex(vertices, pose, max, maxY, plane, red, green, blue, 255, 1, 0, light, facing);
                vertex(vertices, pose, max, minY, plane, red, green, blue, 255, 1, 1, light, facing);
            }
            case SOUTH -> {
                float z = 1.0F - plane;
                vertex(vertices, pose, max, minY, z, red, green, blue, 255, 0, 1, light, facing);
                vertex(vertices, pose, max, maxY, z, red, green, blue, 255, 0, 0, light, facing);
                vertex(vertices, pose, min, maxY, z, red, green, blue, 255, 1, 0, light, facing);
                vertex(vertices, pose, min, minY, z, red, green, blue, 255, 1, 1, light, facing);
            }
            case WEST -> {
                vertex(vertices, pose, plane, minY, max, red, green, blue, 255, 0, 1, light, facing);
                vertex(vertices, pose, plane, maxY, max, red, green, blue, 255, 0, 0, light, facing);
                vertex(vertices, pose, plane, maxY, min, red, green, blue, 255, 1, 0, light, facing);
                vertex(vertices, pose, plane, minY, min, red, green, blue, 255, 1, 1, light, facing);
            }
            case EAST -> {
                float x = 1.0F - plane;
                vertex(vertices, pose, x, minY, min, red, green, blue, 255, 0, 1, light, facing);
                vertex(vertices, pose, x, maxY, min, red, green, blue, 255, 0, 0, light, facing);
                vertex(vertices, pose, x, maxY, max, red, green, blue, 255, 1, 0, light, facing);
                vertex(vertices, pose, x, minY, max, red, green, blue, 255, 1, 1, light, facing);
            }
            default -> {
                // FACING is a horizontal property; nothing to draw for up or down.
            }
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int red, int green, int blue, int alpha, float u, float v, int light, Direction normal) {
        vertices.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), normal.getStepX(), normal.getStepY(), normal.getStepZ())
                .endVertex();
    }
}
