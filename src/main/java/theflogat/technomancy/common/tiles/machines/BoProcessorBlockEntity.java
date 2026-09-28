package theflogat.technomancy.common.tiles.machines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileBOProcessor} (the Mana Purifier), ported to the upstream numbers: a 1,000,000 Mana
 * buffer it fills itself from every {@link ManaPool} in its 9x9 layer at up to 5,000 a tick, then
 * the shared 60-tick ore cycle paid per working tick.
 *
 * <p>The original charged {@code multiplier * 150 + 1500 * reprocess} per tick, where the
 * multiplier is the ore's stage. This port's shared cycle already reduces the stage and pass
 * counts to its own cost unit, so the per-tick price here is {@value #MANA_PER_COST} Mana times
 * that unit, which keeps the fee proportional to the upstream figure; the total per job is the
 * product over 60 ticks.</p>
 */
public final class BoProcessorBlockEntity extends ProcessorBlockEntity implements ManaReceiver {

    public static final int MANA_CAPACITY = 1_000_000;
    public static final int PULL_PER_TICK = 5_000;
    public static final int MANA_PER_COST = 150;
    public static final int PULL_RADIUS = 4;

    private static final String TAG_MANA = "Mana";

    private int mana;

    public BoProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(theflogat.technomancy.compat.botania.BotaniaContent.PROCESSOR_BO_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoProcessorBlockEntity processor) {
        processor.perform(level, pos);
        processor.processTick(level, pos, state);
    }

    private void perform(Level level, BlockPos pos) {
        if (mana >= MANA_CAPACITY) {
            return;
        }
        for (int dx = -PULL_RADIUS; dx <= PULL_RADIUS; dx++) {
            for (int dz = -PULL_RADIUS; dz <= PULL_RADIUS; dz++) {
                if (mana >= MANA_CAPACITY) {
                    return;
                }
                BlockEntity neighbour = level.getBlockEntity(pos.offset(dx, 0, dz));
                if (neighbour == null) {
                    continue;
                }
                ManaReceiver receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, null)
                        .resolve().orElse(null);
                if (receiver instanceof ManaPool pool) {
                    int taken = Math.min(pool.getCurrentMana(), Math.min(MANA_CAPACITY - mana, PULL_PER_TICK));
                    if (taken > 0) {
                        pool.receiveMana(-taken);
                        mana += taken;
                        setChanged();
                    }
                }
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
        if (price <= 0 || mana < price) {
            return false;
        }
        mana -= price;
        setChanged();
        return true;
    }

    @Override
    public int fuelAmount() {
        return mana;
    }

    @Override
    public int fuelCapacity() {
        return MANA_CAPACITY;
    }

    // ---- Botania ManaReceiver ----

    @Override
    public Level getManaReceiverLevel() {
        return level;
    }

    @Override
    public BlockPos getManaReceiverPos() {
        return worldPosition;
    }

    @Override
    public int getCurrentMana() {
        return mana;
    }

    @Override
    public boolean isFull() {
        return mana >= MANA_CAPACITY;
    }

    @Override
    public void receiveMana(int amount) {
        mana = Math.max(0, Math.min(MANA_CAPACITY, mana + amount));
        setChanged();
    }

    @Override
    public boolean canReceiveManaFromBursts() {
        return true;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == BotaniaForgeCapabilities.MANA_RECEIVER) {
            return BotaniaForgeCapabilities.MANA_RECEIVER.orEmpty(cap, LazyOptional.of(() -> this));
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mana = tag.getInt(TAG_MANA);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_MANA, mana);
    }
}
