package theflogat.technomancy.common.blocks.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import theflogat.technomancy.common.machines.biome.BiomeTarget;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.machines.BiomeMorpherBlockEntity;

/**
 * {@code BlockBiomeMorpher}. One block, three targets: right-clicking steps through them, which
 * is exactly what the original's right-click on its three metadata values did.
 */
public class BiomeMorpherBlock extends BaseEntityBlock {

    public static final EnumProperty<BiomeTarget> TARGET = EnumProperty.create("target", BiomeTarget.class);

    public BiomeMorpherBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TARGET, BiomeTarget.MAGICAL_FOREST));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<
            net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TARGET);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BiomeMorpherBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.BIOME_MORPHER.get(),
                        BiomeMorpherBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BiomeTarget next = state.getValue(TARGET).next();
        level.setBlock(pos, state.setValue(TARGET, next), Block.UPDATE_ALL);
        player.displayClientMessage(Component.translatable(next.translationKey()), true);
        return InteractionResult.CONSUME;
    }
}
