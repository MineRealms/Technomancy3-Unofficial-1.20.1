package theflogat.technomancy.common.tiles.technom.existence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The Existence fountain ({@code TileExistenceFountain}): makes Existence power over time and is
 * the largest producer in the network.
 *
 * <p>{@link ExistencePylonBlockEntity} only recognises {@link IExistenceProducer}, so the fountain
 * has to be one or the power it makes stays inside it: the ritual that creates it is the only
 * way to obtain one, and without a producer side the whole Existence chain — fountain, pylon,
 * burner, users — would have no source that is not a mob grinder.</p>
 */
public final class ExistenceFountainBlockEntity extends BlockEntity implements IExistenceProducer {

    public static final int POWER_CAP = 1_000_000;
    public static final int PRODUCTION = 500;

    private static final String TAG_POWER = "power";

    private int power;

    public ExistenceFountainBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.EXISTENCE_FOUNTAIN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExistenceFountainBlockEntity fountain) {
        if (fountain.power < POWER_CAP) {
            fountain.power = Math.min(POWER_CAP, fountain.power + PRODUCTION);
            fountain.setChanged();
        }
    }

    public int power() {
        return power;
    }

    public int powerCap() {
        return POWER_CAP;
    }

    public int maxRate() {
        return PRODUCTION * 4;
    }

    public boolean isRunning() {
        return power < POWER_CAP;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public int getPowerCap() {
        return POWER_CAP;
    }

    @Override
    public int getMaxRate() {
        return PRODUCTION * 4;
    }

    @Override
    public void addPower(int value) {
        power = Math.max(0, Math.min(POWER_CAP, power + value));
        setChanged();
    }

    @Override
    public boolean canInput() {
        return false;
    }

    @Override
    public boolean canOutput() {
        return power > 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_POWER, power);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power = tag.getInt(TAG_POWER);
    }
}
