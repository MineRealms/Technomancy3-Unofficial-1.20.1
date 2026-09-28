package theflogat.technomancy.common.blocks.technom.existence;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity;

/** {@code BlockExistenceFountain}: produces Existence and trails enchantment particles. */
public class ExistenceFountainBlock extends BaseEntityBlock {

    public ExistenceFountainBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExistenceFountainBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.EXISTENCE_FOUNTAIN.get(),
                        ExistenceFountainBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ExistenceFountainBlockEntity fountain)
                || !fountain.isRunning()) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ENCHANT,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 4.0,
                    pos.getY() + 2.0,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 4.0,
                    0.0, -random.nextDouble(), 0.0);
        }
    }
}
