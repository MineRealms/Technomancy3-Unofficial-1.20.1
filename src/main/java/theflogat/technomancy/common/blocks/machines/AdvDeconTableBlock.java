package theflogat.technomancy.common.blocks.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity;

/**
 * {@code BlockAdvDeconTable}. A waist-high table with one slot: right-click to place an object
 * on it, right-click again to take it back, shift-right-click with the potency gem to halve the
 * break time.
 *
 * <p>The 1.7.10 block accepted any held item and relied on the entity to notice it had no
 * aspects; insertion here requires an object the table can actually break, which keeps junk out
 * of the slot and out of automation.</p>
 */
public class AdvDeconTableBlock extends BaseEntityBlock {

    /** The original model is a low table; the collision box matches the drawn shape. */
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.75, 1.0);

    public AdvDeconTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvDeconTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.ADV_DECON_TABLE.get(),
                        AdvDeconTableBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AdvDeconTableBlockEntity table) {
            table.setOwner(player);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AdvDeconTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            return useUpgrade(level, pos, player, table, held);
        }
        if (table.items().getStackInSlot(AdvDeconTableBlockEntity.SLOT).isEmpty()) {
            return insert(level, player, table, held);
        }
        return eject(level, player, table);
    }

    private static InteractionResult useUpgrade(Level level, BlockPos pos, Player player,
            AdvDeconTableBlockEntity table, ItemStack held) {
        if (held.is(TechnomItems.POTENCY_GEM.get()) && !table.isBoosted()) {
            if (!level.isClientSide) {
                table.setBoosted(true);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && table.isBoosted()) {
            if (!level.isClientSide) {
                table.setBoosted(false);
                ItemStack refund = new ItemStack(TechnomItems.POTENCY_GEM.get());
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult insert(Level level, Player player, AdvDeconTableBlockEntity table,
            ItemStack held) {
        if (held.isEmpty() || !AdvDeconTableBlockEntity.decomposable(held)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            table.items().setStackInSlot(AdvDeconTableBlockEntity.SLOT, held.copy());
            held.setCount(0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult eject(Level level, Player player, AdvDeconTableBlockEntity table) {
        if (!level.isClientSide) {
            ItemStack out = table.items().extractItem(AdvDeconTableBlockEntity.SLOT, Integer.MAX_VALUE, false);
            if (!out.isEmpty() && !player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AdvDeconTableBlockEntity table) {
            table.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
