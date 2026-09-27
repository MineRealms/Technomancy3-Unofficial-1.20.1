package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.common.machines.fusor.FusorSides;
import theflogat.technomancy.common.tiles.machines.EssentiaFusorBlockEntity;

/**
 * Draws the fusor's four marking slots: the aspect of every marked side, on the top surface, at
 * the quadrant that side is clicked on.
 *
 * <p>Without this the machine is unusable — the whole configuration is invisible in a static
 * model, and the original had a renderer for exactly this reason. The output slot gets a paper
 * backing (Thaumcraft's own jar-label texture) so it cannot be mistaken for an input.</p>
 */
public final class EssentiaFusorRenderer implements BlockEntityRenderer<EssentiaFusorBlockEntity> {

    private static final ResourceLocation LABEL = new ResourceLocation("thaumcraft", "textures/models/label.png");

    /** Just above the 12/16 top surface, so it does not z-fight the model. */
    private static final float PLANE = 0.7540F;
    private static final float LABEL_PLANE = 0.7530F;
    private static final float ICON_HALF = 0.09F;
    private static final float LABEL_HALF = 0.115F;
    /** Distance from the centre to a slot's centre; matches the click thresholds. */
    private static final float OFFSET = 0.3F;

    public EssentiaFusorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EssentiaFusorBlockEntity fusor, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FusorSides sides = fusor.sides();
        for (Direction slot : FusorSides.SLOTS) {
            FusorSides.SideType type = sides.type(slot);
            if (type == FusorSides.SideType.NONE) {
                continue;
            }
            float centreX = 0.5F + OFFSET * slot.getStepX();
            float centreZ = 0.5F + OFFSET * slot.getStepZ();
            if (type == FusorSides.SideType.OUTPUT) {
                quad(buffer.getBuffer(RenderType.entityCutoutNoCull(LABEL)), poseStack,
                        centreX, centreZ, LABEL_HALF, LABEL_PLANE, 0xFFFFFF);
            }
            AspectId aspect = sides.aspect(slot);
            AspectDefinition definition = aspect == null ? null : AspectApi.get(aspect);
            if (definition != null) {
                quad(buffer.getBuffer(RenderType.entityCutoutNoCull(definition.texture())), poseStack,
                        centreX, centreZ, ICON_HALF, PLANE, definition.color());
            }
        }
    }

    /** A flat, upward-facing square centred on the top surface at {@code (x, z)}. */
    private static void quad(VertexConsumer vertices, PoseStack poseStack, float x, float z,
            float half, float y, int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int light = LightTexture.FULL_BRIGHT;
        PoseStack.Pose pose = poseStack.last();
        vertex(vertices, pose, x - half, y, z + half, red, green, blue, 0.0F, 1.0F, light);
        vertex(vertices, pose, x + half, y, z + half, red, green, blue, 1.0F, 1.0F, light);
        vertex(vertices, pose, x + half, y, z - half, red, green, blue, 1.0F, 0.0F, light);
        vertex(vertices, pose, x - half, y, z - half, red, green, blue, 0.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int red, int green, int blue, float u, float v, int light) {
        vertices.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
