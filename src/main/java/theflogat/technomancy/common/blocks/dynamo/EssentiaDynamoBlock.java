package theflogat.technomancy.common.blocks.dynamo;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;

/**
 * The essentia dynamo block: facing, working state and the four right-click interactions.
 *
 * <p>Like the original there is no GUI. Everything is done by right-clicking: a potency gem
 * installs the upgrade, one of the three redstone programming items sets the redstone mode, and
 * a sneaking empty hand either turns the energy output (on the output face itself) or takes the
 * gem back out (on any other face). Any item in {@link #WRENCHES} also turns the output, but
 * that tag is empty unless a wrench-providing mod fills it, which is why the bare-handed gesture
 * exists at all - without it, fixing defect A-19 would have left rotation unreachable.</p>
 */
public class EssentiaDynamoBlock extends BaseEntityBlock {

    /** The single face energy leaves through. Was a {@code byte} on the block entity. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /**
     * Whether the dynamo is burning fuel right now.
     *
     * <p>Deliberate addition: neither {@code TileDynamoBase} nor {@code TileEssentiaDynamo} had
     * any notion of being active, and their only way of telling a client anything was a full
     * block update every tick (defect A-6). A block state flag costs one packet per transition
     * and is visible to comparators, Jade and resource packs.</p>
     */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Items allowed to turn the output face. Populated by whatever wrench-providing mods are present. */
    public static final TagKey<Item> WRENCHES =
            TagKey.create(Registries.ITEM, new ResourceLocation(Technomancy.MOD_ID, "tools/wrench"));

    /**
     * Base plus the shaft and spouts above it.
     *
     * <p>The original was a plain full cube ({@code BlockDynamoBase} only turned off opacity and
     * normal-block rendering), which does not match a model that is 8 pixels tall for its upper
     * half.</p>
     */
    private static final VoxelShape SHAPE = Shapes.or(
            box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
            box(2.0, 8.0, 2.0, 14.0, 16.0, 14.0));

    public EssentiaDynamoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /**
     * The output points out of the surface the dynamo was placed against, so dropping one on the
     * floor gives the original's default of {@link Direction#UP}. A sneaking empty-handed click
     * on that face, or a wrench, retargets it afterwards.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaDynamoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // Generation, the essentia pull and energy output are all server-side; the client only
        // draws what the last description packet told it.
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.ESSENTIA_DYNAMO.get(),
                        EssentiaDynamoBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * Deliberate addition: charge of the energy buffer, 0-15. The original had no comparator
     * output on any of its machines.
     */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof EssentiaDynamoBlockEntity dynamo)) {
            return 0;
        }
        long stored = dynamo.energy().ledger().stored();
        return stored <= 0 ? 0
                : (int) (stored * 14 / EssentiaDynamoBlockEntity.ENERGY_CAPACITY) + 1;
    }

    /**
     * Every branch mutates server-side only; the client just reports success so the arm swings.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EssentiaDynamoBlockEntity dynamo)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (held.is(TechnomItems.POTENCY_GEM.get())) {
            return installGem(level, pos, player, dynamo, held);
        }
        if (held.is(WRENCHES)) {
            return turn(level, pos, dynamo);
        }
        InteractionResult redstone =
                RedstoneControl.interact(dynamo.redstone(), level, pos, player, held);
        if (redstone != InteractionResult.PASS) {
            return redstone;
        }
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            // The output face turns the dynamo; the other five take the gem out, so the two
            // gestures cannot be confused with one another.
            return hit.getDirection() == dynamo.facing()
                    ? turn(level, pos, dynamo)
                    : removeGem(level, pos, player, dynamo, hit.getDirection());
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult installGem(Level level, BlockPos pos, Player player,
            EssentiaDynamoBlockEntity dynamo, ItemStack gem) {
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

    /**
     * Takes the gem back out.
     *
     * <p>Any face but the energy output, so the gesture stays away from the side a cable or
     * machine is attached to. The original took the gem out on a plain sneaking empty-handed
     * right-click on any face, handled in a shared block base class.</p>
     */
    private static InteractionResult removeGem(Level level, BlockPos pos, Player player,
            EssentiaDynamoBlockEntity dynamo, Direction clicked) {
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

    private static InteractionResult turn(Level level, BlockPos pos, EssentiaDynamoBlockEntity dynamo) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        dynamo.cycleFacing();
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /**
     * Hands back the installed gem and the programming item on break.
     *
     * <p>Both are separate items the player put in, so destroying them with the machine would be
     * a plain loss. The essentia in the buffer is not returned, exactly as before.</p>
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EssentiaDynamoBlockEntity dynamo) {
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

    /**
     * States the real rates, generated from the constants.
     *
     * <p>The original's {@code getInfo()} was a hard-coded "360 RF/t For Four Times The Fuel"
     * against an actual 320 (defect A-5).</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines,
            TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.essentia_dynamo.rate",
                EssentiaDynamoBlockEntity.BASE_RATE, EssentiaDynamoBlockEntity.MAX_OUTPUT)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.essentia_dynamo.boost",
                EssentiaDynamoBlockEntity.BOOSTED_RATE, EssentiaDynamoBlockEntity.BOOSTED_UNITS)
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("block.technom.essentia_dynamo.per_unit",
                EssentiaFuelTable.Q_PER_FUEL_POINT).withStyle(ChatFormatting.DARK_GRAY));
    }
}
