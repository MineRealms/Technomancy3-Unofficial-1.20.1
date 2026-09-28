package theflogat.technomancy.common.tiles.technom.existence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileExistencePylon}: relays Existence within a 7x1x7 layer. It pulls from producers,
 * pushes to consumers, and equalises with other pylons, all bounded by its tier's rate.
 */
public final class ExistencePylonBlockEntity extends BlockEntity implements IExistenceTransmitter {

    private static final String TAG_POWER = "power";
    private static final int HORIZONTAL = 7;

    private int power;

    public ExistencePylonBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.EXISTENCE_PYLON.get(), pos, state);
    }

    private ExistenceTier tier() {
        return getBlockState().getBlock() instanceof theflogat.technomancy.common.blocks.technom.existence.ExistencePylonBlock pylon
                ? pylon.tier() : ExistenceTier.BASIC;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExistencePylonBlockEntity pylon) {
        pylon.relay(level, pos);
    }

    private void relay(Level level, BlockPos pos) {
        int rate = tier().rate();
        if (power < rate) {
            for (BlockEntity neighbour : neighbours(level, pos)) {
                if (neighbour instanceof IExistenceProducer producer && producer.canOutput()
                        && !(neighbour instanceof ExistencePylonBlockEntity)) {
                    int moved = Math.min(rate - power, Math.min(producer.getMaxRate(), producer.getPower()));
                    if (moved > 0) {
                        producer.addPower(-moved);
                        power += moved;
                    }
                }
                if (power >= rate) {
                    break;
                }
            }
        }
        if (power <= 0) {
            return;
        }
        for (BlockEntity neighbour : neighbours(level, pos)) {
            if (neighbour instanceof IExistenceConsumer consumer && consumer.canInput()) {
                int moved = Math.min(power, Math.min(consumer.getMaxRate(), consumer.getPowerCap() - consumer.getPower()));
                if (moved > 0) {
                    consumer.addPower(moved);
                    power -= moved;
                }
            }
        }
        if (power <= 0) {
            return;
        }
        for (BlockEntity neighbour : neighbours(level, pos)) {
            if (neighbour instanceof ExistencePylonBlockEntity other && other != this) {
                int moved = Math.min(power, Math.min(other.getMaxRate(), other.tier().rate() - other.power));
                if (moved > 0) {
                    other.addPower(moved);
                    power -= moved;
                }
            }
        }
        setChanged();
    }

    private java.util.List<BlockEntity> neighbours(Level level, BlockPos pos) {
        java.util.List<BlockEntity> found = new java.util.ArrayList<>();
        for (int dx = -HORIZONTAL; dx <= HORIZONTAL; dx++) {
            for (int dz = -HORIZONTAL; dz <= HORIZONTAL; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockEntity entity = level.getBlockEntity(pos.offset(dx, dy, dz));
                    if (entity != null) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    public int getPower() {
        return power;
    }

    @Override
    public int getMaxRate() {
        return tier().rate();
    }

    public void addPower(int value) {
        power = Math.max(0, Math.min(tier().rate(), power + value));
        setChanged();
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
