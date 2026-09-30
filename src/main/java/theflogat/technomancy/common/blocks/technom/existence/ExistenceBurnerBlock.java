package theflogat.technomancy.common.blocks.technom.existence;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceBurnerBlockEntity;

/** {@code BlockExistenceBurner}: static (no power) and dynamic (needs energy) variants. */
public class ExistenceBurnerBlock extends BaseEntityBlock {

    private final boolean dynamic;

    public ExistenceBurnerBlock(boolean dynamic, Properties properties) {
        super(properties);
        this.dynamic = dynamic;
    }

    public boolean dynamic() {
        return dynamic;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Right-clicking with one of the three programming items sets the redstone mode. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ExistenceBurnerBlockEntity burner)) {
            return InteractionResult.PASS;
        }
        return RedstoneControl.interact(burner.redstone(), level, pos, player, player.getItemInHand(hand));
    }

    /** Hands back the programming item, as every other programmable machine does. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ExistenceBurnerBlockEntity burner) {
            ItemStack programming = burner.redstone().refund();
            if (!programming.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, programming);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExistenceBurnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.EXISTENCE_BURNER.get(),
                        ExistenceBurnerBlockEntity::serverTick);
    }
}
