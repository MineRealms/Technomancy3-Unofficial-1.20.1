package theflogat.technomancy.common.blocks.technom.existence;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceUserBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceUserBlockEntity.Mode;

/** {@code BlockExistenceUser}: one block per action. */
public class ExistenceUserBlock extends BaseEntityBlock {

    private final Mode mode;

    public ExistenceUserBlock(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExistenceUserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.EXISTENCE_USER.get(),
                        ExistenceUserBlockEntity::serverTick);
    }
}
