package theflogat.technomancy.common.blocks.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;

/**
 * The quantum jar block: shape, comparator output and the right-click interactions.
 *
 * <p>The original had no GUI and did everything through right-clicks, which is kept. Its
 * seven hand-written branches for phials, labels and jar items collapse into one path here,
 * because TC4R's {@code EssentiaContainerApi} already knows the capacity and transfer rules
 * of every carrier item.</p>
 */
public class QuantumJarBlock extends BaseEntityBlock {

    /** {@code setBlockBounds(0.1875, 0, 0.1875, 0.8125, 0.75, 0.8125)}. */
    private static final VoxelShape SHAPE = box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public QuantumJarBlock(Properties properties) {
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
        return new QuantumJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Only the server pulls essentia; the client has nothing to tick.
        return level.isClientSide ? null : createTickerHelper(type, TechnomBlockEntities.QUANTUM_JAR.get(),
                QuantumJarBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * Deliberate addition: the original had no comparator output at all. The formula is
     * TC4R's, so a quantum jar and a warded jar read the same way at the same fill fraction.
     */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof QuantumJarBlockEntity jar ? jar.comparatorOutput() : 0;
    }

    /**
     * Every branch mutates only on the server. The original modified state on both sides
     * while playing its sounds only on the client, which left the client holding ghost
     * contents until the next sync (defect A-3).
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof QuantumJarBlockEntity jar)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (held.isEmpty() && player.isSecondaryUseActive()) {
            return clearByHand(level, pos, jar);
        }
        if (EssentiaContainerApi.isLabel(held)) {
            return applyLabel(level, pos, player, hand, jar, held);
        }
        if (EssentiaContainerApi.capacity(held) > 0 && held.getCount() == 1) {
            return exchangeWithCarrier(level, pos, jar, held);
        }
        return InteractionResult.PASS;
    }

    /**
     * Sneak with an empty hand: take the label off a labelled jar, or wipe an unlabelled one.
     *
     * <p>Wiping clears the remembered aspect too. The original left it behind, so the jar
     * read as empty while still advertising a type to tubes and goggles (defect A-2).</p>
     */
    private static InteractionResult clearByHand(Level level, BlockPos pos, QuantumJarBlockEntity jar) {
        boolean labelled = jar.filter() != null;
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (labelled) {
            jar.clearFilter();
            play(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM);
        } else {
            jar.reset();
            play(level, pos, SoundEvents.BREWING_STAND_BREW);
        }
        return InteractionResult.CONSUME;
    }

    private static InteractionResult applyLabel(Level level, BlockPos pos, Player player,
            InteractionHand hand, QuantumJarBlockEntity jar, ItemStack label) {
        if (jar.filter() != null) {
            return InteractionResult.PASS;
        }
        // A blank label adopts whatever the jar already holds, which is how the original let
        // you label a jar that was already filled.
        AspectId selected = EssentiaContainerApi.labelAspect(label).orElseGet(jar::aspect);
        if (selected == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!jar.setFilter(selected)) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative()) {
            label.shrink(1);
        }
        play(level, pos, SoundEvents.BOOK_PAGE_TURN);
        return InteractionResult.CONSUME;
    }

    /**
     * Phial or warded-jar item in hand: fill the block from it, or fill it from the block.
     *
     * <p>{@code EssentiaContainerApi} enforces each carrier's own rules — a phial moves
     * exactly eight units or nothing, a jar item moves partial amounts — so neither side can
     * be left holding a fraction that the other refused.</p>
     */
    private static InteractionResult exchangeWithCarrier(Level level, BlockPos pos,
            QuantumJarBlockEntity jar, ItemStack carrier) {
        AspectId held = EssentiaContainerApi.contents(carrier).amounts().keySet()
                .stream().findFirst().orElse(null);

        if (held != null) {
            int drained = EssentiaContainerApi.extract(level, carrier, held,
                    EssentiaContainerApi.capacity(carrier), EssentiaTransferMode.SIMULATE);
            int room = jar.store().add(held, drained, true);
            // Phials are all-or-nothing, so a partial fit must not consume the phial.
            if (room > 0 && (EssentiaContainerApi.capacity(carrier) != 8 || room == drained)) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                int taken = EssentiaContainerApi.extract(level, carrier, held, room, EssentiaTransferMode.EXECUTE);
                if (taken > 0) {
                    jar.store().add(held, taken, false);
                    play(level, pos, SoundEvents.BOTTLE_EMPTY);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.PASS;
            }
        }

        AspectId stored = jar.aspect();
        if (stored == null) {
            return InteractionResult.PASS;
        }
        int fits = EssentiaContainerApi.insert(level, carrier, stored,
                jar.amount(), EssentiaTransferMode.SIMULATE);
        if (fits <= 0 || jar.store().amount(stored) < fits) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int inserted = EssentiaContainerApi.insert(level, carrier, stored, fits, EssentiaTransferMode.EXECUTE);
        if (inserted <= 0) {
            return InteractionResult.PASS;
        }
        // Debit exactly what the carrier took, never the requested amount.
        jar.store().take(stored, inserted, false);
        play(level, pos, SoundEvents.BOTTLE_FILL);
        return InteractionResult.CONSUME;
    }

    private static void play(Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
    }
}
