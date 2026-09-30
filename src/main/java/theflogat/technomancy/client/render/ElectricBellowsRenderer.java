package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.blocks.machines.ElectricBellowsBlock;
import theflogat.technomancy.common.tiles.machines.ElectricBellowsBlockEntity;

/**
 * Draws the electric bellows, a port of {@code TileElectricBellowsRenderer} and
 * {@code ModelElectricBellows}.
 *
 * <p>A renderer is unavoidable: the bag is scaled by how inflated the bellows is and the two planks
 * slide apart with it, so the body moves every frame. A block model cannot express that, which is
 * why upstream's {@code BlockElectricBellowsRenderer.renderWorldBlock} returned {@code false} and
 * every pixel of the block came from the special renderer.</p>
 *
 * <p>The transform chain is the original's own, in the original's order, and it is deliberately
 * <em>not</em> the {@code scale(-1,-1,1); translate(-.5,-1.5,.5)} chain most of this batch shares.
 * The bellows' {@code translateFromOrientation} is {@code translate(x+.5, y-.5, z+.5)} followed by
 * a rotation about Y, with no mirroring at all, so that is what is reproduced here - the block
 * position is the only part dropped, because a {@link PoseStack} already carries it. Changing the
 * chain would move the machine into the floor.</p>
 *
 * <p>One original call has no direct {@code PoseStack} equivalent: {@code Bag.setRotationPoint(0,
 * .5F, 0)} moves the bag's pivot from its baked {@code (0, 16, 0)} to {@code (0, .5, 0)}. A
 * {@code ModelPart} pose is baked once, so the same effect is produced by translating the pose by
 * the pivot difference; the bag carries no rotation, so moving the pivot and translating by the
 * difference are the same thing.</p>
 */
public final class ElectricBellowsRenderer implements BlockEntityRenderer<ElectricBellowsBlockEntity> {

    /** {@code Ref.MODEL_ELECTRIC_BELLOWS_TEXTURE}. 128x64, the only 128-wide sheet in the batch. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/electricbellows.png");

    /**
     * The pose translation that stands in for {@code Bag.setRotationPoint(0, .5F, 0)}: the bag's
     * baked pivot is {@code (0, 16, 0)}, so the pose moves it by {@code (.5 - 16) / 16} blocks.
     * It is applied after the bag's scale, which is what puts the shift in pre-scale model space,
     * exactly where the original's pivot change sat.
     */
    private static final float BAG_PIVOT_SHIFT = (0.5F - 16.0F) * TechneModel.UNIT;

    /**
     * {@code ModelElectricBellows}, box for box. All five carried {@code mirror = true}. The boxes
     * are listed in the original's field order, but {@link #draw} draws them in the order the tile
     * renderer did, which is not the same order.
     *
     * <p>One call is deliberately dropped: the bag's own {@code setTextureSize(64, 32)}. Upstream
     * followed the Techne-generated pattern of {@code addBox} first and {@code setTextureSize}
     * second, so the bag's smaller sheet is an export artifact rather than an intent - and the
     * sheet settles it. {@code electricbellows.png} is 128x64, the nozzle's {@code uv (0, 36)} with
     * a 12x6 unwrap needs rows 36..42 that only exist on a 64-tall sheet, and the bag's
     * {@code uv (48, 0)} with an 80x44 unwrap ends at column 128 exactly. 128x64 is the only sheet
     * on which all five parts land inside the image; a 64x32 bag would sample off both edges.</p>
     */
    private static final TechneModel MODEL = TechneModel.builder(128, 64)
            .part("BottomPlank").uv(0, 0).mirror(true).at(0.0F, 22.0F, 0.0F)
                    .box(-6.0F, 0.0F, -6.0F, 12, 2, 12).end()
            .part("MiddlePlank").uv(0, 0).mirror(true).at(0.0F, 16.0F, 0.0F)
                    .box(-6.0F, -1.0F, -6.0F, 12, 2, 12).end()
            .part("TopPlank").uv(0, 0).mirror(true).at(0.0F, 8.0F, 0.0F)
                    .box(-6.0F, 0.0F, -6.0F, 12, 2, 12).end()
            .part("Bag").uv(48, 0).mirror(true).at(0.0F, 16.0F, 0.0F)
                    .box(-10.0F, -12.03333F, -10.0F, 20, 24, 20).end()
            .part("Nozzle").uv(0, 36).mirror(true).at(0.0F, 16.0F, 6.0F)
                    .box(-2.0F, -2.0F, 0.0F, 4, 4, 2).end()
            .build();

    public ElectricBellowsRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ElectricBellowsBlockEntity bellows, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = bellows.getBlockState().getValue(ElectricBellowsBlock.FACING);
        draw(inflation(bellows.getLevel(), bellows.getBlockPos()), facing, pose, buffers,
                packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileElectricBellowsRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockElectricBellowsRenderer.renderInventoryBlock} only set up a
     * transform and called {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileElectricBellows}, so the item and the block were the same drawing.</p>
     *
     * <p>{@code scale} is the original's {@code inflation}, 0..1. The original read it from
     * {@code TileElectricBellows.inflation}, an oscillator advanced in {@code updateEntity}, which
     * 1.7.10 ran on both sides - so it was never synced, it was recomputed. The port has no client
     * ticker on this block, so {@link #inflation} runs the same recurrence from
     * {@code Level.getGameTime} instead: same sawtooth, no block entity field and no packet.</p>
     */
    public static void draw(float scale, Direction facing, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        // The sheet is RGBA but every used texel is either fully opaque or fully clear, so the
        // alpha test is the only thing the original's glEnable(GL_ALPHA_TEST) was buying; its
        // glEnable(GL_BLEND) had nothing to blend and entityTranslucent would only put a solid
        // machine through the translucent pass.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        float tscale = 0.125F + scale * 0.875F;

        pose.pushPose();
        pose.translate(0.5F, -0.5F, 0.5F);
        applyFacing(pose, facing);
        pose.translate(0.0F, 1.0F, 0.0F);

        // The bag is drawn first, scaled, with its pivot moved. See BAG_PIVOT_SHIFT.
        pose.pushPose();
        pose.scale(0.5F, (scale + 0.1F) / 2.0F, 0.5F);
        pose.translate(0.0F, BAG_PIVOT_SHIFT, 0.0F);
        MODEL.render("Bag", pose, vertices, packedLight, packedOverlay);
        pose.popPose();

        // The original undid its +1 here, so the planks and the two fixed boxes sit in the plain
        // oriented space.
        pose.translate(0.0F, -1.0F, 0.0F);

        pose.pushPose();
        pose.translate(0.0F, -tscale / 2.0F + 0.5F, 0.0F);
        MODEL.render("TopPlank", pose, vertices, packedLight, packedOverlay);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0F, tscale / 2.0F - 0.5F, 0.0F);
        MODEL.render("BottomPlank", pose, vertices, packedLight, packedOverlay);
        pose.popPose();

        // The original's ModelBase.render(): MiddlePlank then Nozzle, both untransformed.
        MODEL.render("MiddlePlank", pose, vertices, packedLight, packedOverlay);
        MODEL.render("Nozzle", pose, vertices, packedLight, packedOverlay);
        pose.popPose();
    }

    /**
     * The original's {@code switch} over 1.7.10 {@code ForgeDirection} ordinals
     * ({@code 2 NORTH, 3 SOUTH, 4 WEST, 5 EAST}). Note that it is not the node fabricator's map:
     * here NORTH is the one that turns, and SOUTH is the model's own orientation.
     */
    private static void applyFacing(PoseStack pose, Direction facing) {
        switch (facing) {
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(270.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            default -> {
                // SOUTH is the model's own orientation.
            }
        }
    }

    /**
     * The original's inventory branch: {@code MathHelper.sin(ticksExisted / 8F) * .3F + .7F},
     * a pulse between 0.4 and 1.0. Shared with the item renderer so both use one formula.
     */
    public static float pulse(float ticks) {
        return Mth.sin(ticks / 8.0F) * 0.3F + 0.7F;
    }

    /**
     * The original's world oscillator, sampled by tick.
     *
     * <p>{@code TileElectricBellows.updateEntity} ran the same four-branch recurrence every tick on
     * both sides, seeded once with {@code 0.35F + rand.nextFloat() * 0.55F}. Running it from
     * {@code gameTime} rather than storing a field keeps the port stateless - nothing to sync,
     * nothing to persist, and two clients see the same bag. The seed is replaced by the block's own
     * hash, which is what gives neighbouring bellows different phases, the job {@code rand} did.</p>
     *
     * <p>The steady state is a sawtooth of exactly {@value #CYCLE_TICKS} ticks, running from
     * {@value #TROUGH} up to {@value #CREST} and back. Upstream's {@code rand} seed only decides
     * how long the transient lasts.</p>
     */
    public static float inflation(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return 1.0F;
        }
        long tick = level.getGameTime() + pos.hashCode();
        return CYCLE[(int) Math.floorMod(tick, (long) CYCLE.length)];
    }

    /** The recurrence's steady state, one entry per tick. */
    static final float[] CYCLE = buildCycle();

    /** {@code 0.35F}, the branch threshold and the lowest the oscillator is ever seeded to. */
    private static final float SEED = 0.35F;
    /** The oscillator's floor and ceiling in the steady state. */
    private static final float TROUGH = 0.375F;
    private static final float CREST = 1.025F;
    /** The recurrence's period, in ticks: measured by {@link #buildCycle()}, pinned by its test. */
    static final int CYCLE_TICKS = 35;
    /** How long the recurrence is left to settle before its period is measured. */
    private static final int WARM_UP_TICKS = 64;
    /** The longest period the search will consider; the real one is {@link #CYCLE_TICKS}. */
    private static final int MAX_PERIOD_TICKS = 128;
    /**
     * How close two ticks have to be to count as the same point in the cycle.
     *
     * <p>The recurrence is only periodic in structure. Neither {@code 0.075F} nor {@code 0.025F} is
     * an exact binary fraction, so every pass drifts by about 4e-7 and two passes are never
     * bit-identical. Demanding equality found no period at all and left the whole warm-up as the
     * "cycle", which showed up in game as the bag jumping once every twenty seconds. Two ticks
     * inside one period are at least 0.025 apart, so this tolerance is orders of magnitude below
     * anything that could be mistaken for a repeat.</p>
     */
    private static final float PERIOD_TOLERANCE = 1.0e-4F;

    /**
     * Runs {@code TileElectricBellows.updateEntity}'s recurrence until it repeats, and keeps one
     * period.
     *
     * <p>The branches are in the original's order, which matters: the direction flip is tested
     * after the step, so a period runs from {@value #TROUGH} up to {@value #CREST} and back down.
     * The seed is the lowest the original could roll, which makes the transient one short rise; it
     * does not affect the steady state, and the port wants the same table for every bellows
     * anyway - the per-block phase comes from the hash in {@link #inflation}.</p>
     */
    static float[] buildCycle() {
        float[] series = new float[WARM_UP_TICKS + 2 * MAX_PERIOD_TICKS];
        float inflation = SEED;
        boolean rising = true;
        for (int tick = 0; tick < series.length; tick++) {
            series[tick] = inflation;
            if (inflation > SEED && !rising) {
                inflation -= 0.075F;
            }
            if (inflation <= SEED && !rising) {
                rising = true;
            }
            if (inflation < 1.0F && rising) {
                inflation += 0.025F;
            }
            if (inflation >= 1.0F && rising) {
                rising = false;
            }
        }
        for (int period = 1; period <= MAX_PERIOD_TICKS; period++) {
            if (repeats(series, period)) {
                float[] cycle = new float[period];
                System.arraycopy(series, series.length - period, cycle, 0, period);
                return cycle;
            }
        }
        throw new IllegalStateException("the bellows oscillator did not repeat within "
                + MAX_PERIOD_TICKS + " ticks; the recurrence or this search has changed");
    }

    /** Whether the last two stretches of {@code period} ticks are the same to within tolerance. */
    private static boolean repeats(float[] series, int period) {
        for (int i = series.length - 2 * period; i < series.length - period; i++) {
            if (Math.abs(series[i] - series[i + period]) > PERIOD_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
