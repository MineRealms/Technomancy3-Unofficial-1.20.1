package theflogat.technomancy.common.blocks.machines;

import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;

/**
 * The energy condenser block: which way it faces, which faces let essentia out, and the
 * right-click interactions.
 *
 * <p>The six output switches live in the block state rather than in the block entity. That is
 * what lets a plain multipart blockstate swap the vent texture onto a face with no
 * {@code BlockEntityRenderer} and no custom item rendering, and it makes the switches save and
 * sync themselves.</p>
 */
public class EnergyCondenserBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty OUT_DOWN = BooleanProperty.create("out_down");
    public static final BooleanProperty OUT_UP = BooleanProperty.create("out_up");
    public static final BooleanProperty OUT_NORTH = BooleanProperty.create("out_north");
    public static final BooleanProperty OUT_SOUTH = BooleanProperty.create("out_south");
    public static final BooleanProperty OUT_WEST = BooleanProperty.create("out_west");
    public static final BooleanProperty OUT_EAST = BooleanProperty.create("out_east");

    /** Indexed by {@link Direction#get3DDataValue()}. */
    private static final BooleanProperty[] OUTPUT =
            {OUT_DOWN, OUT_UP, OUT_NORTH, OUT_SOUTH, OUT_WEST, OUT_EAST};

    public EnergyCondenserBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty output : OUTPUT) {
            state = state.setValue(output, false);
        }
        registerDefaultState(state);
    }

    public static BooleanProperty output(Direction face) {
        return OUTPUT[face.get3DDataValue()];
    }

    /** Whether essentia may leave through this face. */
    public static boolean outputs(BlockState state, Direction face) {
        return state.hasProperty(output(face)) && state.getValue(output(face));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUT_DOWN, OUT_UP, OUT_NORTH, OUT_SOUTH, OUT_WEST, OUT_EAST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Rotation carries the output switches around with the block, then clears whichever one
     * ends up on the front. The original's wrench only bumped the facing metadata and left the
     * switches where they were, so turning a condenser could put an enabled output on the face
     * that is not allowed to have one.
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction face : Direction.Plane.HORIZONTAL) {
            result = result.setValue(output(rotation.rotate(face)), state.getValue(output(face)));
        }
        return clearFrontOutput(result);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction face : Direction.Plane.HORIZONTAL) {
            result = result.setValue(output(mirror.mirror(face)), state.getValue(output(face)));
        }
        return clearFrontOutput(result);
    }

    private static BlockState clearFrontOutput(BlockState state) {
        return state.setValue(output(state.getValue(FACING)), false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCondenserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // Conversion and transfer are server-only; the client has nothing to tick.
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.ENERGY_CONDENSER.get(),
                        EnergyCondenserBlockEntity::serverTick);
    }

    /**
     * Every branch mutates on the server only.
     *
     * <p>The block entity is resolved and checked first. The original checked for null only on
     * the sneaking path and then dereferenced it unconditionally two lines later, so
     * right-clicking a condenser whose block entity had gone missing threw (defect A-12).</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EnergyCondenserBlockEntity condenser)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        RedstoneMode programmed = held.isEmpty() ? null : RedstoneMode.byProgrammingItem(held.getItem());
        if (programmed != null) {
            return program(level, pos, player, condenser, held, programmed);
        }
        if (player.isSecondaryUseActive()) {
            return toggleOutput(state, level, pos, hit.getDirection());
        }
        return fillCarrier(level, pos, condenser, held);
    }

    /**
     * Gunpowder, redstone dust and a redstone torch set the three redstone modes, as in the
     * original. A mode the player had already programmed is handed back as its item, so
     * changing your mind costs nothing.
     */
    private static InteractionResult program(Level level, BlockPos pos, Player player,
            EnergyCondenserBlockEntity condenser, ItemStack held, RedstoneMode mode) {
        if (mode == condenser.redstoneMode()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean wasModified = condenser.isRedstoneModified();
        RedstoneMode previous = condenser.setRedstoneMode(mode);
        if (previous == null) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (wasModified) {
            giveOrDrop(level, pos, player, new ItemStack(previous.programmingItem()));
        }
        // The mode has no visible form on the block, unlike the output faces, so say it.
        player.displayClientMessage(Component.translatable("technom.condenser.redstone_mode",
                Component.translatable(mode.translationKey())), true);
        play(level, pos, SoundEvents.LEVER_CLICK);
        return InteractionResult.CONSUME;
    }

    /**
     * Sneak-clicking a face switches essentia output on or off there. The front face is never
     * an output, which is the original rule and also what keeps the front texture and the vent
     * texture from ever being asked for at once.
     */
    private static InteractionResult toggleOutput(BlockState state, Level level, BlockPos pos,
            Direction face) {
        if (face == state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // UPDATE_ALL, not UPDATE_CLIENTS: an adjacent tube has to re-evaluate the connection.
        level.setBlock(pos, state.cycle(output(face)), Block.UPDATE_ALL);
        play(level, pos, SoundEvents.LEVER_CLICK);
        return InteractionResult.CONSUME;
    }

    /**
     * A phial or jar item in hand is filled from the buffer.
     *
     * <p>The original hard-coded "an empty phial and at least eight units". TC4R's
     * {@code EssentiaContainerApi} already knows every carrier's capacity and its all-or-nothing
     * rule, so the amount comes from the carrier and the block is debited by exactly what the
     * carrier reports taking — never by what was requested.</p>
     */
    private static InteractionResult fillCarrier(Level level, BlockPos pos,
            EnergyCondenserBlockEntity condenser, ItemStack carrier) {
        if (EssentiaContainerApi.capacity(carrier) <= 0 || carrier.getCount() != 1) {
            return InteractionResult.PASS;
        }
        int fits = EssentiaContainerApi.insert(level, carrier, EnergyCondenserBlockEntity.POTENTIA,
                condenser.amount(), EssentiaTransferMode.SIMULATE);
        if (fits <= 0 || condenser.amount() < fits) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int inserted = EssentiaContainerApi.insert(level, carrier, EnergyCondenserBlockEntity.POTENTIA,
                fits, EssentiaTransferMode.EXECUTE);
        if (inserted <= 0) {
            return InteractionResult.PASS;
        }
        condenser.store().take(EnergyCondenserBlockEntity.POTENTIA, inserted, false);
        play(level, pos, SoundEvents.BOTTLE_FILL);
        return InteractionResult.CONSUME;
    }

    /** Gives back the programming item that was in the machine when it is broken. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof EnergyCondenserBlockEntity condenser
                && condenser.isRedstoneModified()) {
            popResource(level, pos, new ItemStack(condenser.redstoneMode().programmingItem()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static void giveOrDrop(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            popResource(level, pos, stack);
        }
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.4F, 1.0F);
    }
}
