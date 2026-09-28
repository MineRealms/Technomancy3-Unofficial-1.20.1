package theflogat.technomancy.common.tiles.technom.existence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The Existence fountain ({@code TileExistenceFountain}): makes Existence power over time. The
 * ritual only needs it to exist and fill; the transmitter/consumer network is a later batch.
 */
public final class ExistenceFountainBlockEntity extends BlockEntity {

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
