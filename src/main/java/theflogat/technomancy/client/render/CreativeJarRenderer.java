package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;

/** The creative jar is always full, so it is drawn as a full column of its aspect. */
public final class CreativeJarRenderer implements BlockEntityRenderer<CreativeJarBlockEntity> {

    public CreativeJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CreativeJarBlockEntity jar, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        AspectId aspect = jar.aspect();
        if (aspect != null) {
            QuantumJarRenderer.renderColumn(aspect, 1.0F, poseStack, buffer, packedLight);
        }
    }
}
