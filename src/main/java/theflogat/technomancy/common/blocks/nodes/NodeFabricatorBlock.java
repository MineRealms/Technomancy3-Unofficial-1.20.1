package theflogat.technomancy.common.blocks.nodes;

import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.common.WandInteractionTarget;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity;
import theflogat.technomancy.common.wands.TechnomWrench;

/**
 * The node fabricator's controller block: the bottom middle of its 1x3x3 slab.
 *
 * <p>Placed looking away from the player, so two fabricators are built facing each other with the
 * node position between them. A wrench turns it - which takes the old shells down and puts new
 * ones up - and the potency gem and the three redstone programming items work as on the
 * dynamos.</p>
 *
 * <p>It is a {@link WandInteractionTarget}, so a wand right-click reaches {@link #use} before the
 * installed focus does, exactly as the original's {@code IWandable} dispatch did: that click is
 * what starts building a node between the pair when there is none.</p>
 */
public class NodeFabricatorBlock extends BaseEntityBlock implements WandInteractionTarget {

    /** Horizontal only: the slab is built across it and the node sits along it. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NodeFabricatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeFabricatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.NODE_FABRICATOR.get(),
                        NodeFabricatorBlockEntity::serverTick);
    }

    /** The shells go up as soon as the controller exists, not on its first tick. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NodeFabricatorBlockEntity machine) {
            machine.formShells(level);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Fill level of the essentia buffer, 0-15. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NodeFabricatorBlockEntity machine)) {
            return 0;
        }
        int held = machine.store().total();
        return held <= 0 ? 0 : held * 14 / NodeFabricatorBlockEntity.ESSENTIA_CAPACITY + 1;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NodeFabricatorBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (WandApi.view(held).isPresent()) {
            // The original's onWandRightClick: a wand click builds a node, but a crouching one
            // is left to the installed focus.
            if (player.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            boolean started = machine.startCreation((net.minecraft.server.level.ServerLevel) level);
            if (started) {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return started ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (held.is(TechnomItems.POTENCY_GEM.get())) {
            if (machine.isBoosted()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                machine.setBoosted(true);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (TechnomWrench.isWrench(held)) {
            if (!level.isClientSide) {
                machine.removeShells(level);
                level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof NodeFabricatorBlockEntity turned) {
                    turned.refreshStructure(level);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        InteractionResult redstone = RedstoneControl.interact(machine.redstone(), level, pos, player, held);
        if (redstone != InteractionResult.PASS) {
            return redstone;
        }
        if (held.isEmpty() && player.isSecondaryUseActive() && machine.isBoosted()) {
            if (!level.isClientSide) {
                machine.setBoosted(false);
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

    /** Breaking the controller takes its shells with it and hands back the loose parts. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NodeFabricatorBlockEntity machine) {
            machine.removeShells(level);
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            if (machine.isBoosted()) {
                Containers.dropItemStack(level, x, y, z, new ItemStack(TechnomItems.POTENCY_GEM.get()));
            }
            ItemStack programming = machine.redstone().refund();
            if (!programming.isEmpty()) {
                Containers.dropItemStack(level, x, y, z, programming);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.node_fabricator.structure",
                NodeFabricatorBlockEntity.PARTNER_DISTANCE).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.node_fabricator.work",
                NodeFabricatorWorkText.RECHARGE, NodeFabricatorWorkText.EXPAND).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("block.technom.node_fabricator.creation",
                NodeFabricatorWorkText.CREATION_MIN, NodeFabricatorWorkText.RITUAL_TICKS)
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Tooltip numbers, kept beside the block so the strings and the rules cannot drift apart. */
    private static final class NodeFabricatorWorkText {
        private static final long RECHARGE = theflogat.technomancy.common.nodes.NodeFabricatorWork.RECHARGE_ENERGY;
        private static final long EXPAND = theflogat.technomancy.common.nodes.NodeFabricatorWork.EXPAND_ENERGY;
        private static final int CREATION_MIN =
                theflogat.technomancy.common.nodes.NodeCreationRules.MIN_TOTAL_ESSENTIA;
        private static final int RITUAL_TICKS =
                theflogat.technomancy.common.nodes.NodeCreationRules.RITUAL_TICKS;
    }
}
