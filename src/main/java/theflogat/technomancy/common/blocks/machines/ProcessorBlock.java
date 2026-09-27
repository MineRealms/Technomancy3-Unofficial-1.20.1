package theflogat.technomancy.common.blocks.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.server.level.ServerPlayer;
import theflogat.technomancy.common.tiles.machines.ProcessorBlockEntity;

/**
 * Shared block behaviour of the ore processors ({@code BlockProcessor}): the working state, the
 * menu, the particles and dropping the inventory.
 *
 * <p>The original decided its textures, its light level and its particles from a boolean on the
 * block entity, which meant every change needed a block-entity update packet. {@link #LIT} is a
 * block state instead, so a plain blockstate file swaps the model and the light engine and the
 * client are updated by the ordinary block update.</p>
 */
public abstract class ProcessorBlock extends BaseEntityBlock {

    /** {@code TileProcessorBase.isActive}. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** {@code BlockProcessor.getLightValue}. */
    public static final int LIT_LIGHT = 12;

    protected ProcessorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ProcessorBlockEntity processor)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            NetworkHooks.openScreen(server, processor, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Drops the two slots. The loot table drops the block alone, so the contents have exactly
     * one owner and a broken machine cannot both drop its items and keep them in the item.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ProcessorBlockEntity processor) {
            processor.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** {@code BlockProcessor.randomDisplayTick}: smoke and flame around a working machine. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.2 + random.nextFloat() * 5.0 / 16.0;
        double z = pos.getZ() + 0.5;
        double offset = random.nextFloat() * 0.5 - 0.25;
        double edge = 0.52;
        spawn(level, x - edge, y, z + offset);
        spawn(level, x + edge, y, z + offset);
        spawn(level, x + offset, y, z - edge);
        spawn(level, x + offset, y, z + edge);
    }

    private static void spawn(Level level, double x, double y, double z) {
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
    }
}
