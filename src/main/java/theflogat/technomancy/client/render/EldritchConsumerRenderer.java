package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity;

/**
 * Draws the eldritch consumer, a port of {@code TileEldritchConsumerRenderer} and
 * {@code ModelEldritchConsumer}.
 *
 * <p>A renderer is unavoidable here because two groups of boxes move: twenty arm segments rock
 * about X on an angle the machine's own update drives, and the rotor spins about Y while it is
 * working. Neither is expressible as a block model, and upstream reached the same conclusion -
 * {@code BlockEldritchConsumerRenderer.renderWorldBlock} returns {@code false} and every pixel of
 * the block comes from the special renderer.</p>
 *
 * <p>The transform chain is the original's, and it is the <em>short</em> one: the usual
 * {@code glTranslatef(-.5F, -1.5F, .5F)} is absent. Only {@code glScalef(-1, -1, 1)} is kept, so
 * a model point {@code (mx, my, mz)} lands at block-local {@code (-mx/16, -my/16, mz/16)} rather
 * than at the {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)} the other machines use. That is not
 * an accident to be "corrected": it is what makes the base - a 16x6x16 box at model x -16..0,
 * z 0..16 - span block x and z exactly 0..1, and the legs reach y 0. The whole model therefore
 * occupies the block's own cube, which is why the inventory renderer below has almost nothing to
 * do. The two negative axes preserve the winding order, so nothing needs culling disabled.</p>
 *
 * <p>The render order is the original's {@code ModelEldritchConsumer.render}: the nine body
 * renderers, then the rotor, then the four groups of five arm segments. Techne had no depth sort,
 * so this order is visible wherever two boxes share a face and it is preserved by declaration
 * order in the {@link TechneModel}.</p>
 */
public final class EldritchConsumerRenderer implements BlockEntityRenderer<EldritchConsumerBlockEntity> {

    /** {@code Ref.MODEL_ELDRITCH_CONSUMER_TEXTURE}. 64x64, so it is a model sheet, not a sprite. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/eldcons.png");

    /** {@code (float) (Math.PI / 2)}, the original's {@code rotateAngleY} on the two side fins. */
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private static final String ROTATOR = "Rotator";

    /**
     * {@code ModelEldritchConsumer}, box for box. Every renderer was built as
     * {@code new ModelRenderer(this, u, v)} - the two-argument constructor, so {@code mirror} is
     * {@code false} throughout, unlike the node fabricator and the flux lamp - and the model's
     * texture size is 64x64. The nine body renderers all sit at their rotation points, so the
     * offsets below are the raw {@code setRotationPoint} arguments; the fins and legs then carry
     * their own {@code rotateAngleX/Y}.
     */
    private static final TechneModel MODEL = build();

    /** The rotor, animated about Y while the machine works. */
    private static final ModelPart ROTATOR_PART = MODEL.part(ROTATOR);

    /** The twenty arm segments, all animated about X together. */
    private static final ModelPart[] SEGMENTS = collectSegments();

    public EldritchConsumerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EldritchConsumerBlockEntity machine, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // TileEldritchConsumer carried panelRotation and cooldown as public fields, synced to the
        // client, and the original read both here: `model.render(0.0625F, panelRotation,
        // cooldown > 0)`. The port keeps the same pair on the block entity - the server owns
        // `cooldown`, the client eases `panelRotation` in its own ticker - and publishes them as
        // working() and panelRotation().
        draw(machine.panelRotation(), machine.working(), pose, buffers, packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileEldritchConsumerRenderer.renderTileEntityAt}, with the block position
     * factored out because a {@link PoseStack} already carries it.
     *
     * <p>Public and static so the inventory renderer can reuse it. Upstream did the same thing by
     * construction: {@code BlockEldritchConsumerRenderer.renderInventoryBlock} does nothing but
     * translate and call {@code TileEntityRendererDispatcher.renderTileEntityAt} on a throwaway
     * {@code TileEldritchConsumer}, so the item and the block are the same drawing by definition
     * rather than by two hand-kept copies.</p>
     *
     * @param panelRotation the arm segments' X rotation in radians; the original read
     *     {@code TileEldritchConsumer.panelRotation}, which is 0 for an inventory render and for a
     *     machine at rest
     * @param working whether the machine is mid-pass; the original read {@code cooldown > 0}, which
     *     is what sets the rotor spinning
     */
    public static void draw(float panelRotation, boolean working, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // One texture, no blending upstream, and a sheet that is three quarters transparent: the
        // alpha test is what makes the model read at all, so cutout rather than solid or
        // translucent. The two negative scales leave the winding order positive, so culling is
        // kept, exactly as the original's default GL_CULL_FACE did.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(TEXTURE));

        // The original set rotateAngleX on every piece of all four groups unconditionally, then
        // rendered them, so every segment carries the same angle.
        for (ModelPart segment : SEGMENTS) {
            segment.xRot = panelRotation;
        }
        // The rotor is only re-aimed while the machine is working. When it is not, the original's
        // `if (go)` left rotateAngleY untouched, so the rotor held its last angle; leaving the
        // field alone reproduces that freeze.
        if (working) {
            ROTATOR_PART.yRot = rotatorSpin();
        }

        pose.pushPose();
        // The original's own chain, in the original's order, and only this much of it.
        pose.scale(-1.0F, -1.0F, 1.0F);
        MODEL.render(pose, vertices, packedLight, packedOverlay);
        pose.popPose();
    }

    /** The original's {@code rotator.rotateAngleY = (System.currentTimeMillis() / 16) % 7}. */
    private static float rotatorSpin() {
        return (float) ((System.currentTimeMillis() / 16L) % 7L);
    }

    private static TechneModel build() {
        TechneModel.Builder builder = TechneModel.builder(64, 64)
                .part("Base").uv(0, 0).at(-16.0F, -12.0F, 0.0F)
                        .box(0.0F, 0.0F, 0.0F, 16, 6, 16).end()
                // The four fins hanging from the top, each a 2x1x3 box turned on two axes.
                .part("Fin1").uv(0, 0).at(-1.5F, -13.5F, 8.0F)
                        .rotate((float) (Math.PI / 2.2), HALF_PI, 0.0F)
                        .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end()
                .part("Fin2").uv(0, 0).at(-14.5F, -13.5F, 8.0F)
                        .rotate((float) (Math.PI / 1.8), HALF_PI, 0.0F)
                        .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end()
                .part("Fin3").uv(0, 0).at(-8.0F, -13.5F, 1.5F)
                        .rotate((float) (Math.PI / 1.8), 0.0F, 0.0F)
                        .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end()
                .part("Fin4").uv(0, 0).at(-8.0F, -13.5F, 14.5F)
                        .rotate((float) (Math.PI / 2.2), 0.0F, 0.0F)
                        .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end()
                // The four feet, which use the second texture offset on the sheet.
                .part("Leg1").uv(48, 0).at(-1.0F, -3.0F, 1.0F)
                        .rotate(0.0F, (float) Math.PI, 0.0F)
                        .box(-1.0F, -3.0F, -1.0F, 2, 6, 2).end()
                .part("Leg2").uv(48, 0).at(-15.0F, -3.0F, 1.0F)
                        .rotate(0.0F, -HALF_PI, 0.0F)
                        .box(-1.0F, -3.0F, -1.0F, 2, 6, 2).end()
                .part("Leg3").uv(48, 0).at(-15.0F, -3.0F, 15.0F)
                        .box(-1.0F, -3.0F, -1.0F, 2, 6, 2).end()
                .part("Leg4").uv(48, 0).at(-1.0F, -3.0F, 15.0F)
                        .rotate(0.0F, HALF_PI, 0.0F)
                        .box(-1.0F, -3.0F, -1.0F, 2, 6, 2).end()
                .part(ROTATOR).uv(0, 0).at(-8.0F, -14.0F, 8.0F)
                        .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end();

        // The four rings of five arm segments, built in the original's own loop order so the
        // declaration order - and therefore the render order - is pieces[0] .. pieces[3].
        for (int i = 0; i < 5; i++) {
            builder.part(segmentName(0, i)).uv(0, 0).at(-8.0F, -5.5F + i, 2.5F + i)
                    .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end();
        }
        for (int i = 0; i < 5; i++) {
            builder.part(segmentName(1, i)).uv(0, 0).at(-8.0F, -5.5F + i, 13.5F - i)
                    .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end();
        }
        for (int i = 0; i < 5; i++) {
            builder.part(segmentName(2, i)).uv(0, 0).at(-2.5F - i, -5.5F + i, 8.0F)
                    .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end();
        }
        for (int i = 0; i < 5; i++) {
            builder.part(segmentName(3, i)).uv(0, 0).at(-13.5F + i, -5.5F + i, 8.0F)
                    .box(-1.0F, -0.5F, -1.5F, 2, 1, 3).end();
        }
        return builder.build();
    }

    /** Resolves the twenty segment parts once, so the per-frame draw never names them. */
    private static ModelPart[] collectSegments() {
        ModelPart[] segments = new ModelPart[20];
        int next = 0;
        for (int group = 0; group < 4; group++) {
            for (int i = 0; i < 5; i++) {
                segments[next++] = MODEL.part(segmentName(group, i));
            }
        }
        return segments;
    }

    private static String segmentName(int group, int index) {
        return "Segment" + group + "_" + index;
    }
}
