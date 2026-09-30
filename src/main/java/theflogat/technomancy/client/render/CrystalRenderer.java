package theflogat.technomancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.client.render.model.TechneModel;
import theflogat.technomancy.common.blocks.technom.CrystalBlock;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CrystalBlockEntity;

/**
 * Draws a ritual crystal, a port of {@code TileCrystalRenderer} and {@code ModelCrystal}.
 *
 * <p>The model is three nested boxes and nothing else, so the only reason this is code rather than
 * JSON is the tint: the original wrapped the whole model in
 * {@code glColor4f(r, g, b, 0.6F)}, and 0.6 alpha cannot survive a block model. {@code
 * blockcrystal.png} is an RGB sheet with no alpha channel of its own, so a {@code render_type} of
 * {@code translucent} would still draw it opaque - and a {@code BlockColors} handler cannot help
 * either, because vanilla's block tinting reads only the red, green and blue bytes of the colour
 * it is handed and throws the alpha away. The whole model is therefore drawn here, and the
 * blockstate model is an empty shell.</p>
 *
 * <p>One upstream defect is deliberately NOT reproduced. {@code ModelCrystal.render} called
 * {@code GL11.glColor4f(c.getRed(), c.getGreen(), c.getBlue(), alpha)} where {@code getRed()}
 * returns 0..255, not 0..1 - so every channel was clamped to 1.0 and nature, fire and water came
 * out correctly by accident (0x00 -> 0, 0xDD -> 1) while light (0x111111) also clamped to pure
 * white, making light and dark crystals indistinguishable. The colours below are the ones the
 * original authored, which is what the fix restores.</p>
 *
 * <p>The transform is the original's, unchanged: {@code scale(-1,-1,1); translate(-.5F,-1.5F,.5F)}
 * maps a model point {@code (mx,my,mz)} in sixteenths to block-local
 * {@code (0.5 - mx/16, 1.5 - my/16, 0.5 + mz/16)}. The 16-unit box spanning {@code my} 8..24
 * therefore lands exactly on the block, which is also why the item render needs no extra framing
 * beyond centring.</p>
 */
public final class CrystalRenderer implements BlockEntityRenderer<CrystalBlockEntity> {

    /** {@code Ref.MODEL_CRYSTAL_TEXTURE}. A 64x64 RGB sheet - hence the alpha below being load-bearing. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Technomancy.MOD_ID, "textures/entity/blockcrystal.png");

    /**
     * The original's alpha. It is not decoration: the sheet is opaque, so this is the only thing
     * that makes a crystal look like a crystal rather than a solid cube.
     */
    private static final float ALPHA = 0.6F;

    private static final String BIG = "crystalBig";
    private static final String MEDIUM = "crystalMed";
    private static final String SMALL = "crystalSma";

    /**
     * {@code ModelCrystal}, box for box. All three renderers share {@code textureOffset (0, 0)} and
     * the sheet is 64x64; none of them sets a rotation point or a rotate angle, and none sets
     * {@code mirror}, so every part is declared at the origin with the default flags. The three
     * boxes are concentric and differ only in width, which is what makes the stack narrow.
     */
    private static final TechneModel MODEL = TechneModel.builder(64, 64)
            .part(BIG).uv(0, 0).box(-8.0F, 8.0F, -8.0F, 16, 16, 16).end()
            .part(MEDIUM).uv(0, 0).box(-4.0F, 8.0F, -4.0F, 8, 16, 8).end()
            .part(SMALL).uv(0, 0).box(-2.0F, 8.0F, -2.0F, 4, 16, 4).end()
            .build();

    /**
     * The authored tint per kind, as 0xRRGGBB. Indexed by {@link Ritual.Type#id()}, so the order
     * is nature, fire, water, light, dark - the same order as the original's metadata.
     */
    private static final int[] TINTS = {0x00DD00, 0xDD0000, 0x0000DD, 0x111111, 0xDDDDDD};

    public CrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrystalBlockEntity crystal, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = crystal.getBlockState();
        if (crystal.getLevel() == null || !(state.getBlock() instanceof CrystalBlock block)) {
            return;
        }
        // The stage is a neighbour query, so it is the same on both sides and nothing is synced.
        draw(CrystalBlock.stage(crystal.getLevel(), crystal.getBlockPos()), block.type(), pose, buffers,
                packedLight, packedOverlay);
    }

    /**
     * The body of {@code TileCrystalRenderer.renderTileEntityAt}, with the block position factored
     * out because a {@link PoseStack} carries it.
     *
     * <p>Public and static so the item renderer can reuse it, exactly as upstream's
     * {@code renderInventoryBlock} reused {@code renderTileEntityAt}.</p>
     *
     * @param stage 0, 1 or 2 - how many crystals are stacked below this one
     */
    public static void draw(int stage, Ritual.Type type, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        int tint = TINTS[type.id()];
        float red = (tint >> 16 & 0xFF) / 255.0F;
        float green = (tint >> 8 & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;

        String part = switch (stage) {
            case 1 -> MEDIUM;
            case 2 -> SMALL;
            default -> BIG;
        };

        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(-0.5F, -1.5F, 0.5F);
        MODEL.render(part, pose, vertices, packedLight, packedOverlay, red, green, blue, ALPHA);
        pose.popPose();
    }
}
