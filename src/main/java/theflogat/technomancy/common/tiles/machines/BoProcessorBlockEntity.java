package theflogat.technomancy.common.tiles.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileBOProcessor}: the Botania ore processor. It spends Mana from an adjacent pool
 * instead of ignis, using the same 60-tick cycle and the same purified-ore chain as the Thaumic
 * processor.
 *
 * <p>One working tick costs {@value #MANA_PER_COST} Mana, i.e. 200 a tick for raw ore and 500
 * for a second pass, so a full job is far cheaper than a mana pool's capacity. The rate is the
 * modern stand-in for the original's {@code Rate} entry, which is not recorded.</p>
 */
public final class BoProcessorBlockEntity extends ProcessorBlockEntity {

    public static final int MANA_PER_COST = 100;

    @Nullable
    private ManaPool pool;

    public BoProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.PROCESSOR_BO.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoProcessorBlockEntity processor) {
        processor.refreshPool(level, pos);
        processor.processTick(level, pos, state);
    }

    private void refreshPool(Level level, BlockPos pos) {
        pool = null;
        for (Direction face : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(face));
            if (neighbour == null) {
                continue;
            }
            pool = null;
            ManaReceiver receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, face.getOpposite())
                    .resolve().orElse(null);
            if (receiver instanceof ManaPool manaPool) {
                pool = manaPool;
                return;
            }
        }
    }

    @Override
    public ProcessingModule module() {
        return ProcessingModule.BOTANIA;
    }

    @Override
    protected boolean payTick(int cost) {
        int price = cost * MANA_PER_COST;
        if (pool == null || pool.getCurrentMana() < price) {
            return false;
        }
        pool.receiveMana(-price);
        return true;
    }

    @Override
    public int fuelAmount() {
        return pool == null ? 0 : pool.getCurrentMana();
    }

    @Override
    public int fuelCapacity() {
        return pool == null ? 0 : pool.getMaxMana();
    }
}
