package theflogat.technomancy.common.blocks.machines;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.machines.fusor.FusorSides;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.machines.EssentiaFusorBlockEntity;

/**
 * {@code BlockEssentiaFusor}: a three-quarter-height block whose top surface has four marking
 * slots, one per horizontal side.
 *
 * <p>Marking follows the original exactly: right-click a quadrant of the top face with a filled
 * phial to make that side an input of the phial's aspect, or with an empty phial to make it the
 * output. Right-clicking an occupied quadrant with an empty hand gives the phial back — the
 * essentia in it included, so marking and unmarking conserve it.</p>
 */
public class EssentiaFusorBlock extends BaseEntityBlock {

    /** {@code setBlockBounds(0, 0, 0, 1, 0.75, 1)}. */
    private static final VoxelShape SHAPE = box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    private static final ResourceLocation PHIAL = new ResourceLocation("thaumcraft", "essence_phial");
    /** {@code EssentiaContainerApi}: a phial is all-or-nothing at exactly eight units. */
    private static final int PHIAL_UNITS = 8;

    public EssentiaFusorBlock(Properties properties) {
        super(properties);
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
        return new EssentiaFusorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.ESSENTIA_FUSOR.get(),
                        EssentiaFusorBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EssentiaFusorBlockEntity fusor)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        InteractionResult programmed = RedstoneControl.interact(fusor.redstone(), level, pos, player, held);
        if (programmed != InteractionResult.PASS) {
            return programmed;
        }
        if (hit.getDirection() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Direction slot = slotAt(hit.getLocation(), pos);
        if (slot == null) {
            return InteractionResult.PASS;
        }
        if (held.isEmpty()) {
            return unmark(level, pos, player, fusor, slot);
        }
        return mark(level, pos, player, fusor, slot, held);
    }

    /**
     * Which quadrant of the top face was clicked, or {@code null} for the middle and the corners.
     * The thresholds are the original's {@code getSideFromPos}.
     */
    @Nullable
    public static Direction slotAt(Vec3 hit, BlockPos pos) {
        double x = hit.x - pos.getX();
        double z = hit.z - pos.getZ();
        boolean middleX = x > 0.33 && x < 0.66;
        boolean middleZ = z > 0.33 && z < 0.66;
        if (middleX && z <= 0.33) {
            return Direction.NORTH;
        }
        if (middleX && z >= 0.66) {
            return Direction.SOUTH;
        }
        if (middleZ && x <= 0.33) {
            return Direction.WEST;
        }
        if (middleZ && x >= 0.66) {
            return Direction.EAST;
        }
        return null;
    }

    /** A filled phial marks an input, an empty one marks the output. */
    private static InteractionResult mark(Level level, BlockPos pos, Player player,
            EssentiaFusorBlockEntity fusor, Direction slot, ItemStack held) {
        if (EssentiaContainerApi.capacity(held) != PHIAL_UNITS || fusor.sides().isOccupied(slot)) {
            return InteractionResult.PASS;
        }
        AspectId offered = EssentiaContainerApi.contents(held).amounts().keySet().stream()
                .findFirst().orElse(null);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        FusorSides sides = fusor.sides();
        boolean marked = offered == null
                ? sides.markOutput(slot, EssentiaFusorBlockEntity.combiner())
                : sides.markInput(slot, offered, EssentiaFusorBlockEntity.combiner());
        if (!marked) {
            // The commonest reason is a second input that does not combine with the first, which
            // is worth saying out loud rather than just doing nothing.
            player.displayClientMessage(Component.translatable("technom.fusor.refused"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        fusor.changedAndSync();
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.4F, 1.0F);
        AspectId output = sides.outputAspect();
        if (output != null && sides.fullyMarked()) {
            player.displayClientMessage(Component.translatable("technom.fusor.recipe",
                    dev.tc4port.thaumcraft.api.aspect.AspectApi.tooltipName(output),
                    fusor.fusionCostQ()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Gives the phial back, with its eight units of essentia if it was an input. */
    private static InteractionResult unmark(Level level, BlockPos pos, Player player,
            EssentiaFusorBlockEntity fusor, Direction slot) {
        if (!fusor.sides().isOccupied(slot)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        FusorSides.Cleared cleared = fusor.sides().clear(slot);
        if (cleared == null) {
            // Refused because this slot or the output still holds essentia; drain it first.
            player.displayClientMessage(Component.translatable("technom.fusor.not_empty"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack refund = phialFor(level, cleared.aspect());
        if (!refund.isEmpty() && !player.getInventory().add(refund)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, refund);
        }
        fusor.changedAndSync();
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /** An empty phial, or one holding eight units of {@code aspect}. */
    private static ItemStack phialFor(Level level, @Nullable AspectId aspect) {
        Item phial = BuiltInRegistries.ITEM.get(PHIAL);
        if (phial == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(phial);
        if (aspect == null) {
            return stack;
        }
        int filled = EssentiaContainerApi.insert(level, stack, aspect, PHIAL_UNITS,
                EssentiaTransferMode.EXECUTE);
        // An aspect the registry no longer knows cannot be bottled; the empty phial is still owed.
        return filled == PHIAL_UNITS ? stack : new ItemStack(phial);
    }

    /**
     * Drops the markers when the machine is broken, as {@code breakBlock} did. Buffered essentia
     * is not recovered — the original lost it too, and unmarking a slot (which does give it back)
     * is refused while anything is stored, so a player is told to drain it first.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof EssentiaFusorBlockEntity fusor) {
            for (Direction slot : FusorSides.SLOTS) {
                if (fusor.sides().isOccupied(slot)) {
                    ItemStack marker = phialFor(level, fusor.sides().aspect(slot));
                    if (!marker.isEmpty()) {
                        popResource(level, pos, marker);
                    }
                }
            }
            ItemStack programming = fusor.redstone().refund();
            if (!programming.isEmpty()) {
                popResource(level, pos, programming);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
