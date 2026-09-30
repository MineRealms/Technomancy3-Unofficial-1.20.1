package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import dev.tc4port.thaumcraft.block.entity.EssentiaTubeBlockEntity;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.tiles.machines.FluxLampBlockEntity;

/**
 * Draws the flux lamp, a port of {@code TileFluxLampRenderer} and {@code ModelFluxLamp}.
 *
 * <p>The lamp is thirteen boxes: a glass core that carries the flux goo, and a post-plus-flange
 * pair on each of the six sides. The core is tinted by how full the tank is, and a side's pair is
 * only drawn when there is something on that side for it to connect to - so the lamp grows a
 * nozzle exactly where a pipe or a tank has been put, which is the only thing telling a player
 * which faces the machine is actually plumbed on.</p>
 *
 * <p>The original read {@code TileFluxLamp.placed}, set by {@code BlockFluxLamp.onBlockPlacedBy},
 * and then asked the world for a neighbour that was an {@code IFluidHandler} on each of the six
 * {@code ForgeDirection}s, plus {@code TileTube} above. The placement flag is kept because a lamp
 * that has not been placed by a player - one spawned by a structure or by {@code /setblock} - did
 * not draw its nozzles either, and reproducing that is free.</p>
 *
 * <p>The original's {@code switch} over {@code ForgeDirection} ordinals looks like it maps the
 * model's front and back nozzles to the wrong world sides, because {@code FrontPost} sits at
 * {@code z = -7} and so reads as the north one. It does not: the whole model is drawn after
 * {@code scale(-1, -1, 1)}, which flips the rotation's output, so the chain sends
 * {@code FrontPost} to {@code x = 1/16} (west), {@code BackPost} to {@code x = 12/16} (east),
 * {@code LeftPost} to {@code z = 1/16} (north) and {@code RightPost} to {@code z = 12/16} (south).
 * The original's mapping is therefore exactly right, and the switch is reproduced verbatim below.
 * {@code probes/client/14_flux_lamp_and_coil.java} pins the four sides down so this cannot be
 * "corrected" again.</p>
 */
public final class FluxLampRenderer implements BlockEntityRenderer<FluxLampBlockEntity> {

    /** {@code Ref.MODEL_FLUX_LAMP_TEXTURE}. 64x32, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/fluxlamp.png");

    /**
     * {@code ModelFluxLamp}, box for box. Every renderer carried {@code mirror = true} and
     * {@code setTextureSize(64, 32)}.
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 32)
            .part("Core").uv(0, 12).mirror(true).at(0.0F, 16.0F, 0.0F)
                    .box(-4.0F, -6.0F, -4.0F, 8, 12, 8).end()
            .part("TopPost").uv(25, 0).mirror(true).at(0.0F, 9.0F, 0.0F)
                    .box(-1.5F, 0.0F, -1.5F, 3, 1, 3).end()
            .part("TopFlat").uv(3, 6).mirror(true).at(0.0F, 8.0F, 0.0F)
                    .box(-2.0F, 0.0F, -2.0F, 4, 1, 4).end()
            .part("BottomPost").uv(25, 0).mirror(true).at(0.0F, 22.0F, 0.0F)
                    .box(-1.5F, 0.0F, -1.5F, 3, 1, 3).end()
            .part("BottomFlat").uv(0, 0).mirror(true).at(0.0F, 23.0F, 0.0F)
                    .box(-2.5F, 0.0F, -2.5F, 5, 1, 5).end()
            .part("FrontPost").uv(24, 0).mirror(true).at(0.0F, 16.0F, -7.0F)
                    .box(-2.0F, -2.0F, 0.0F, 4, 4, 3).end()
            .part("FrontFlat").uv(0, 0).mirror(true).at(0.0F, 16.0F, -8.0F)
                    .rotate(1.570796F, 0.0F, 0.0F).box(-2.5F, 0.0F, -2.5F, 5, 1, 5).end()
            .part("RightPost").uv(24, 0).mirror(true).at(4.0F, 16.0F, 0.0F)
                    .rotate(0.0F, 1.570796F, 0.0F).box(-2.0F, -2.0F, 0.0F, 4, 4, 3).end()
            .part("RightFlat").uv(0, 0).mirror(true).at(7.0F, 16.0F, 0.0F)
                    .rotate(1.570796F, 1.570796F, 0.0F).box(-2.5F, 0.0F, -2.5F, 5, 1, 5).end()
            .part("LeftPost").uv(24, 0).mirror(true).at(-7.0F, 16.0F, 0.0F)
                    .rotate(0.0F, 1.570796F, 0.0F).box(-2.0F, -2.0F, 0.0F, 4, 4, 3).end()
            .part("LeftFlat").uv(0, 0).mirror(true).at(-8.0F, 16.0F, 0.0F)
                    .rotate(1.570796F, 1.570796F, 0.0F).box(-2.5F, 0.0F, -2.5F, 5, 1, 5).end()
            .part("BackPost").uv(24, 0).mirror(true).at(0.0F, 16.0F, 4.0F)
                    .box(-2.0F, -2.0F, 0.0F, 4, 4, 3).end()
            .part("BackFlat").uv(0, 0).mirror(true).at(0.0F, 16.0F, 7.0F)
                    .rotate(1.570796F, 0.0F, 0.0F).box(-2.5F, 0.0F, -2.5F, 5, 1, 5).end()
            .build();

    public FluxLampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluxLampBlockEntity lamp, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(lamp.placed(), lamp.tankAmount(), lamp.getLevel(), lamp.getBlockPos(), pose, buffers,
                packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileFluxLampRenderer.renderTileEntityAt}, with the block position factored
     * out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockFluxLampRenderer.renderInventoryBlock} does nothing but translate
     * and call {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileFluxLamp}, which is why the item shows a bare lamp with no nozzles and no tint -
     * a fresh tile entity has {@code placed = false} and an empty tank.</p>
     *
     * <p>The model already occupies the block's own {@code [0, 1]} cube - its core is centred on
     * {@code (0.5, 0.5, 0.5)} and its base sits on {@code y = 0} - so unlike the node fabricator
     * there is nothing to re-centre or rescale for the inventory.</p>
     *
     * @param level the world, or {@code null} for an inventory render, where no nozzle is drawn
     * @param pos the lamp's position, read only when {@code placed} and {@code level} are both set
     */
    public static void draw(boolean placed, int amount, @Nullable Level level, @Nullable BlockPos pos,
            PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // The core's own sheet is mostly transparent, so it goes through cutout: the alpha test
        // discards those texels instead of letting them darken what is behind them, and the solid
        // box keeps writing depth. The nozzles are small and rarely overlap, so they can be
        // blended the way the original's glEnable(GL_BLEND) did.
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        VertexConsumer blended = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));

        if (placed && level != null && pos != null) {
            drawNozzles(level, pos, pose, blended, packedLight, packedOverlay);
        }

        // The original's glColor3f(amount, 0, amount) sat *inside* `if (tank.getFluidAmount() > 0)`.
        // Applying it unconditionally is a visible bug rather than a harmless simplification: the
        // core is drawn through cutout, and two thirds of its texels are alpha 0, so with an empty
        // tank `amount` is 0 and the surviving third is painted (0, 0, 0, 1) - a solid black box
        // where the glass should be. An empty lamp has to stay untinted white.
        if (amount > 0) {
            // Anything from one mB up clamps to full magenta, exactly as glColor3f did.
            float tint = Math.min(1.0F, amount);
            MODEL.render("Core", pose, solid, packedLight, packedOverlay, tint, 0.0F, tint, 1.0F);
        } else {
            MODEL.render("Core", pose, solid, packedLight, packedOverlay);
        }
        pose.popPose();
    }

    /**
     * The original's {@code renderNozzles}: a pair above when a tube is there, and a pair on each
     * side that holds a fluid handler. {@code ForgeDirection} ordinals were
     * {@code 0 DOWN, 1 UP, 2 NORTH, 3 SOUTH, 4 WEST, 5 EAST}; the top is handled separately
     * because it keys off a tube rather than a tank.
     */
    private static void drawNozzles(Level level, BlockPos pos, PoseStack pose, VertexConsumer vertices,
            int packedLight, int packedOverlay) {
        if (level.getBlockEntity(pos.above()) instanceof EssentiaTubeBlockEntity) {
            pair(pose, vertices, packedLight, packedOverlay, "TopPost", "TopFlat");
        }
        for (Direction side : Direction.values()) {
            if (side == Direction.UP || !holdsFluid(level, pos, side)) {
                continue;
            }
            switch (side) {
                case DOWN -> pair(pose, vertices, packedLight, packedOverlay, "BottomPost", "BottomFlat");
                case NORTH -> pair(pose, vertices, packedLight, packedOverlay, "LeftPost", "LeftFlat");
                case SOUTH -> pair(pose, vertices, packedLight, packedOverlay, "RightPost", "RightFlat");
                case WEST -> pair(pose, vertices, packedLight, packedOverlay, "FrontPost", "FrontFlat");
                case EAST -> pair(pose, vertices, packedLight, packedOverlay, "BackPost", "BackFlat");
                default -> {
                }
            }
        }
    }

    private static void pair(PoseStack pose, VertexConsumer vertices, int packedLight, int packedOverlay,
            String post, String flange) {
        MODEL.render(post, pose, vertices, packedLight, packedOverlay);
        MODEL.render(flange, pose, vertices, packedLight, packedOverlay);
    }

    /** {@code WorldHelper.isAdjacentFluidHandler}: the neighbour offers a fluid handler on our face. */
    private static boolean holdsFluid(Level level, BlockPos pos, Direction side) {
        BlockPos at = pos.relative(side);
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockEntity neighbour = level.getBlockEntity(at);
        return neighbour != null
                && neighbour.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).isPresent();
    }
}
