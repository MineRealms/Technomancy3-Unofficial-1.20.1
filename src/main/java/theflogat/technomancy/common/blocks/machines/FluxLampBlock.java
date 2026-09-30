package theflogat.technomancy.common.blocks.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.machines.FluxLampBlockEntity;

/**
 * {@code BlockFluxLamp}. The lamp itself has no interaction: it silently watches for an infusion
 * altar nearby, so the whole block is a model, a block entity and the ordo pipe that feeds it.
 */
public class FluxLampBlock extends BaseEntityBlock {

    public FluxLampBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The lamp's whole body is code-drawn - see FluxLampRenderer - so the JSON model is an
        // empty shell that only carries the break particle.
        return RenderShape.MODEL;
    }

    /**
     * {@code BlockFluxLamp.onBlockPlacedBy}: the renderer only draws the six nozzles once the lamp
     * has been placed by a player, so a lamp spawned by a structure or {@code /setblock} stays bare.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluxLampBlockEntity lamp) {
            lamp.setPlaced(true);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxLampBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.FLUX_LAMP.get(),
                        FluxLampBlockEntity::serverTick);
    }
}
