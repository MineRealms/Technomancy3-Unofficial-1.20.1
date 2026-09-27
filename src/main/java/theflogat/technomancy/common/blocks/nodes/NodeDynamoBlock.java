package theflogat.technomancy.common.blocks.nodes;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.blocks.dynamo.EssentiaDynamoBlock;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;

/**
 * The node dynamo block. Same four right-click interactions as the essentia dynamo (potency gem,
 * redstone programming items, sneak-empty-hand turn/remove gem, {@code technom:tools/wrench}).
 *
 * <p>{@code BlockNodeDynamo.onBlockPlacedBy} forced {@code facing = 0} (down) whatever the
 * player did. Here the output points into the block the dynamo was placed against, which on a
 * floor is that same "down".</p>
 */
public class NodeDynamoBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Base slab on the output side plus the emitter column of {@code ModelNodeDynamo}. */
    private static final java.util.Map<Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(Map.of(
            Direction.DOWN, Shapes.or(box(0, 0, 0, 16, 8, 16), box(3, 8, 3, 13, 14, 13)),
            Direction.UP, Shapes.or(box(0, 8, 0, 16, 16, 16), box(3, 2, 3, 13, 8, 13)),
            Direction.NORTH, Shapes.or(box(0, 0, 0, 16, 16, 8), box(3, 3, 8, 13, 13, 14)),
            Direction.SOUTH, Shapes.or(box(0, 0, 8, 16, 16, 16), box(3, 3, 2, 13, 13, 8)),
            Direction.WEST, Shapes.or(box(0, 0, 0, 8, 16, 16), box(8, 3, 3, 14, 13, 13)),
            Direction.EAST, Shapes.or(box(8, 0, 0, 16, 16, 16), box(2, 3, 3, 8, 13, 13))));

    public NodeDynamoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeDynamoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.NODE_DYNAMO.get(), NodeDynamoBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NodeDynamoBlockEntity dynamo)) {
            return 0;
        }
        long stored = dynamo.energy().ledger().stored();
        return stored <= 0 ? 0 : (int) (stored * 14 / NodeDynamoBlockEntity.ENERGY_CAPACITY) + 1;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NodeDynamoBlockEntity dynamo)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(TechnomItems.POTENCY_GEM.get())) {
            if (dynamo.isBoosted()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                dynamo.setBoosted(true);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(EssentiaDynamoBlock.WRENCHES)) {
            return turn(level, pos, dynamo);
        }
        InteractionResult redstone = RedstoneControl.interact(dynamo.redstone(), level, pos, player, held);
        if (redstone != InteractionResult.PASS) {
            return redstone;
        }
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            if (hit.getDirection() == dynamo.facing()) {
                return turn(level, pos, dynamo);
            }
            if (!dynamo.isBoosted()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                dynamo.setBoosted(false);
                ItemStack gem = new ItemStack(TechnomItems.POTENCY_GEM.get());
                if (!player.getInventory().add(gem)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gem);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult turn(Level level, BlockPos pos, NodeDynamoBlockEntity dynamo) {
        if (!level.isClientSide) {
            dynamo.cycleFacing();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Hands back the gem and the programming item; buffered Vis is lost, as before. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NodeDynamoBlockEntity dynamo) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            if (dynamo.isBoosted()) {
                Containers.dropItemStack(level, x, y, z, new ItemStack(TechnomItems.POTENCY_GEM.get()));
            }
            ItemStack programming = dynamo.redstone().refund();
            if (!programming.isEmpty()) {
                Containers.dropItemStack(level, x, y, z, programming);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.node_dynamo.rate",
                NodeDynamoBlockEntity.BASE_RATE, NodeDynamoBlockEntity.RANGE).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.node_dynamo.per_vis",
                NodeDynamoBlockEntity.VIS_FUEL_POINTS).withStyle(ChatFormatting.DARK_GRAY));
    }
}
