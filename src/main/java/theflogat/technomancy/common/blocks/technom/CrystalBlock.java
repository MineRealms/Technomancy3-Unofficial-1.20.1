package theflogat.technomancy.common.blocks.technom;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CrystalBlockEntity;

/**
 * One crystal of a ritual frame ({@code BlockCrystal}): three stacking stages, one per kind.
 *
 * <p>The 1.7.10 block packed the five kinds into metadata 0..4; here each kind is its own block,
 * which keeps the creative tab, the models and {@code RitualFrames} free of item-NBT plumbing.
 * The numbering (nature, fire, water, light, dark) is still {@link Ritual.Type}'s 0..4 order.</p>
 *
 * <p>The shape narrows as crystals stack: a lone crystal fills its block, one with a crystal
 * under it is a 0.25..0.75 column, and one two crystals up is 0.375..0.625. The stage stays a pure
 * neighbour query, so the block entity below holds nothing and never ticks.</p>
 *
 * <p>Everything visible is drawn by code, so the render shape is {@code INVISIBLE} - the same
 * effect as upstream's {@code BlockCrystalRenderer.renderWorldBlock} returning {@code false}. The
 * blockstate model survives only as an empty shell carrying the break-particle texture; it is
 * never drawn. The reason a renderer is needed at all is that the crystal is a 0.6-alpha tinted
 * cube and {@code blockcrystal.png} is an RGB sheet with no alpha channel, so no combination of
 * {@code render_type} and a block colour handler can reproduce it - vanilla drops the alpha of a
 * {@code BlockColors} result on the floor.</p>
 */
public class CrystalBlock extends BaseEntityBlock {

    private static final VoxelShape STAGE_0 = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final VoxelShape STAGE_1 = Shapes.box(0.25, 0.0, 0.25, 0.75, 1.0, 0.75);
    private static final VoxelShape STAGE_2 = Shapes.box(0.375, 0.0, 0.375, 0.625, 1.0, 0.625);

    private final Ritual.Type type;

    public CrystalBlock(Ritual.Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Ritual.Type type() {
        return type;
    }

    /** How many crystals of this kind are stacked below this one, capped at two. */
    public static int stage(BlockGetter level, BlockPos pos) {
        int stage = 0;
        if (level.getBlockState(pos.below()).getBlock() instanceof CrystalBlock) {
            stage++;
            if (level.getBlockState(pos.below(2)).getBlock() instanceof CrystalBlock) {
                stage++;
            }
        }
        return stage;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (stage(level, pos)) {
            case 1 -> STAGE_1;
            case 2 -> STAGE_2;
            default -> STAGE_0;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalBlockEntity(pos, state);
    }
}
