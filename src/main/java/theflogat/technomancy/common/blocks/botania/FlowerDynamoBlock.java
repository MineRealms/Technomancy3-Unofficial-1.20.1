package theflogat.technomancy.common.blocks.botania;

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
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.botania.FlowerDynamoBlockEntity;
import theflogat.technomancy.common.wands.TechnomWrench;

/**
 * {@code BlockFlowerDynamo}, the "Hippie Dynamo". The output face is the only energy face; a
 * wrench, or a sneaking empty-handed click on that face, turns it. Right-clicking with one of the
 * redstone programming items sets the redstone mode and a Potency Gem installs the boost, exactly
 * as on the other dynamos.
 */
public class FlowerDynamoBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public FlowerDynamoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
        return new FlowerDynamoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, theflogat.technomancy.compat.botania.BotaniaContent.FLOWER_DYNAMO_BE.get(),
                        FlowerDynamoBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FlowerDynamoBlockEntity dynamo)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(TechnomItems.POTENCY_GEM.get())) {
            return installGem(level, pos, player, dynamo, held);
        }
        if (TechnomWrench.isWrench(held)) {
            return turn(level, pos, dynamo);
        }
        InteractionResult redstone =
                RedstoneControl.interact(dynamo.redstone(), level, pos, player, held);
        if (redstone != InteractionResult.PASS) {
            return redstone;
        }
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            return hit.getDirection() == dynamo.facing()
                    ? turn(level, pos, dynamo)
                    : removeGem(level, pos, player, dynamo, hit.getDirection());
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult installGem(Level level, BlockPos pos, Player player,
            FlowerDynamoBlockEntity dynamo, ItemStack gem) {
        if (dynamo.isBoosted()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        dynamo.setBoosted(true);
        if (!player.isCreative()) {
            gem.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult removeGem(Level level, BlockPos pos, Player player,
            FlowerDynamoBlockEntity dynamo, Direction clicked) {
        if (!dynamo.isBoosted() || clicked == dynamo.facing()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        dynamo.setBoosted(false);
        ItemStack gem = new ItemStack(TechnomItems.POTENCY_GEM.get());
        if (!player.getInventory().add(gem)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gem);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult turn(Level level, BlockPos pos, FlowerDynamoBlockEntity dynamo) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        dynamo.cycleFacing();
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FlowerDynamoBlockEntity dynamo) {
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
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines,
            TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.flower_dynamo.rate",
                FlowerDynamoBlockEntity.BASE_RATE, FlowerDynamoBlockEntity.BOOSTED_RATE)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.flower_dynamo.wrench")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
