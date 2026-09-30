package theflogat.technomancy.common.blocks.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import theflogat.technomancy.common.machines.consumer.ConsumerRange;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity;

/**
 * {@code BlockEldritchConsumer}. A block carries no slot: it eats what is around and below it,
 * so the only interaction is a sneak-right-click that steps through the six working volumes.
 */
public class EldritchConsumerBlock extends BaseEntityBlock {

    public EldritchConsumerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EldritchConsumerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // Both sides tick. The original's updateEntity had a client half that eased the arm
        // segments toward -pi/4 while the machine worked; without a client ticker here the panel
        // would sit at rest no matter what the server did.
        return createTickerHelper(type, TechnomBlockEntities.ELDRITCH_CONSUMER.get(),
                level.isClientSide
                        ? EldritchConsumerBlockEntity::clientTick
                        : EldritchConsumerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.isShiftKeyDown()
                || !(level.getBlockEntity(pos) instanceof EldritchConsumerBlockEntity consumer)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ConsumerRange range = consumer.cycleRange();
            int side = range.radius() * 2 + 1;
            String depth = range.height() < 0 ? "bedrock" : Integer.toString(range.height());
            player.displayClientMessage(
                    Component.translatable("technom.consumer.range", range.label(), side, side, depth), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
