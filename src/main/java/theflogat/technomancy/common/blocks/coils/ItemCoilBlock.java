package theflogat.technomancy.common.blocks.coils;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.coils.CoilBlockEntity;
import theflogat.technomancy.common.tiles.coils.ItemCoilBlockEntity;

/**
 * {@code itemTransmitter}. Ported from {@code BlockItemTransmitter}: right-click with any item to
 * make it the filter (a ghost copy; the held stack is not consumed), sneak with an empty hand to
 * clear it.
 */
public class ItemCoilBlock extends CoilBlock {

    public ItemCoilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemCoilBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends CoilBlockEntity> type() {
        return TechnomBlockEntities.ITEM_COIL.get();
    }

    @Override
    protected InteractionResult useFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player,
            ItemStack held) {
        if (held.isEmpty() || !(coil instanceof ItemCoilBlockEntity items) || !items.filter().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            items.setFilter(held);
            player.displayClientMessage(Component.translatable("message.technom.coil.filter_set", held.getHoverName()),
                    true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult removeFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player) {
        if (!(coil instanceof ItemCoilBlockEntity items) || items.filter().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            items.setFilter(ItemStack.EMPTY);
            player.displayClientMessage(Component.translatable("message.technom.coil.filter_cleared"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    protected Component filterName(CoilBlockEntity coil) {
        return coil instanceof ItemCoilBlockEntity items && !items.filter().isEmpty()
                ? items.filter().getHoverName()
                : null;
    }

    @Override
    protected boolean canFeed(Level level, BlockPos target, Direction facing) {
        BlockEntity be = level.isLoaded(target) ? level.getBlockEntity(target) : null;
        return be != null && !(be instanceof CoilBlockEntity)
                && be.getCapability(ForgeCapabilities.ITEM_HANDLER, facing.getOpposite()).isPresent();
    }
}
