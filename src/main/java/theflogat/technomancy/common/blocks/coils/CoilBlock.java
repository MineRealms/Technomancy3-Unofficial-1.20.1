package theflogat.technomancy.common.blocks.coils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.blocks.dynamo.EssentiaDynamoBlock;
import theflogat.technomancy.common.coils.CoilLinks;
import theflogat.technomancy.common.items.coils.CoilCouplerItem;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.coils.CoilBlockEntity;

/**
 * Shared block of the two coils. Ported from {@code BlockCoilTransmitter}.
 *
 * <p>Interactions, in the order they are tried: Potency Gem installs; a wrench turns the coil to
 * the next neighbour it can feed; a redstone programming item sets the mode; the subclass's
 * filter handling; sneaking with an empty hand removes the filter, or failing that the gem; an
 * empty hand shows the coil's status, which stands in for the original's WAILA tooltip.</p>
 */
public abstract class CoilBlock extends BaseEntityBlock {

    /** Direction from the coil to the block it feeds; the original's {@code facing} field. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /**
     * Potency Gem installed (the original's {@code boost}). In the block state because it
     * decides {@link #isSignalSource}, and so the model can tint the upper ring as the original
     * renderer did.
     */
    public static final BooleanProperty GEM = BooleanProperty.create("gem");

    /** Base on the fed block, 6x14x6 pixels; the original used 10x16x10 for every facing. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.DOWN, box(5, 0, 5, 11, 14, 11),
            Direction.UP, box(5, 2, 5, 11, 16, 11),
            Direction.NORTH, box(5, 5, 0, 11, 11, 14),
            Direction.SOUTH, box(5, 5, 2, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 14, 11, 11),
            Direction.EAST, box(2, 5, 5, 16, 11, 11)));

    protected CoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(GEM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GEM);
    }

    /** Stands on the block it was placed against, as the original's {@code OPPOSITES[side]}. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    protected abstract BlockEntityType<? extends CoilBlockEntity> type();

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, type(), CoilBlockEntity::serverTick);
    }

    // ---- Potency Gem redstone output ----

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(GEM);
    }

    /** Weak power only; see the design note for why the original's strong power was dropped. */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(GEM) && level.getBlockEntity(pos) instanceof CoilBlockEntity coil && coil.isSignalling()
                ? 15 : 0;
    }

    // ---- interaction ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CoilBlockEntity coil)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof CoilCouplerItem) {
            return InteractionResult.PASS;
        }
        if (held.is(TechnomItems.POTENCY_GEM.get())) {
            return state.getValue(GEM) ? InteractionResult.PASS : installGem(state, level, pos, player, held);
        }
        if (held.is(EssentiaDynamoBlock.WRENCHES)) {
            return turn(state, level, pos);
        }
        if (RedstoneMode.byProgrammingItem(held.getItem()) != null) {
            if (state.getValue(GEM)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.technom.coil.gem_blocks_redstone"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            // Never falls through to the filter: a programming item is not an item-coil filter,
            // as in the original, whose base block consumed these clicks first.
            return RedstoneControl.interact(coil.redstone(), level, pos, player, held);
        }
        InteractionResult filter = useFilter(coil, level, pos, player, held);
        if (filter != InteractionResult.PASS) {
            return filter;
        }
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            InteractionResult removed = removeFilter(coil, level, pos, player);
            if (removed != InteractionResult.PASS) {
                return removed;
            }
            return state.getValue(GEM) ? removeGem(state, level, pos, player) : InteractionResult.PASS;
        }
        if (held.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(status(coil), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Sets the filter from the held item. */
    protected abstract InteractionResult useFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player,
            ItemStack held);

    /** Clears the filter, handing back anything that was consumed to set it. */
    protected abstract InteractionResult removeFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player);

    /** The filter as the player should read it, or {@code null} when there is none. */
    @Nullable
    protected abstract Component filterName(CoilBlockEntity coil);

    /** Anything the filter holds that must drop with the block. */
    protected ItemStack filterRefund(CoilBlockEntity coil) {
        return ItemStack.EMPTY;
    }

    private Component status(CoilBlockEntity coil) {
        Component filter = filterName(coil);
        Component mode = coil.hasGem()
                ? Component.translatable("message.technom.coil.signalling")
                : Component.translatable(coil.redstone().mode().translationKey());
        return Component.translatable("message.technom.coil.status", coil.links().size(), CoilLinks.MAX_LINKS,
                filter == null ? Component.translatable("message.technom.coil.no_filter") : filter, mode);
    }

    private static InteractionResult installGem(BlockState state, Level level, BlockPos pos, Player player,
            ItemStack gem) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(GEM, true), Block.UPDATE_ALL);
        if (!player.isCreative()) {
            gem.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable("message.technom.coil.gem_installed"), true);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult removeGem(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(GEM, false), Block.UPDATE_ALL);
        give(level, pos, player, new ItemStack(TechnomItems.POTENCY_GEM.get()));
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /**
     * Turns to the next face whose neighbour the coil can feed, as the original's
     * {@code onWrenched}; if none qualifies, to the next face at all.
     */
    private InteractionResult turn(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction current = state.getValue(FACING);
        Direction next = Direction.from3DDataValue((current.get3DDataValue() + 1) % 6);
        for (int step = 1; step < 6; step++) {
            Direction candidate = Direction.from3DDataValue((current.get3DDataValue() + step) % 6);
            if (canFeed(level, pos.relative(candidate), candidate)) {
                next = candidate;
                break;
            }
        }
        level.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /** Whether the coil could feed the block at {@code target} through the face toward it. */
    protected abstract boolean canFeed(Level level, BlockPos target, Direction facing);

    protected static void give(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }

    /**
     * Drops what the player put into the coil - gem, programming item, consumed filter - on any
     * removal, explosions included. The block itself comes from the loot table.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof CoilBlockEntity coil) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            if (state.getValue(GEM)) {
                Containers.dropItemStack(level, x, y, z, new ItemStack(TechnomItems.POTENCY_GEM.get()));
            }
            ItemStack programming = coil.redstone().refund();
            if (!programming.isEmpty()) {
                Containers.dropItemStack(level, x, y, z, programming);
            }
            ItemStack filter = filterRefund(coil);
            if (!filter.isEmpty()) {
                Containers.dropItemStack(level, x, y, z, filter);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.coil.hint").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.coil.gem").withStyle(ChatFormatting.DARK_GRAY));
    }
}
