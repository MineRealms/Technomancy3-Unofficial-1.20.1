package theflogat.technomancy.common.tiles.air;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The host-tracking half of {@code TileFakeAirCore}: a fake-air block remembers the machine that
 * placed it and removes itself once that machine is gone.
 *
 * <p>The 1.7.10 base stored the host class by name ({@code Class.forName}) and re-read it from a
 * sync packet. This keeps the host position only and checks it on the server, so no class name
 * ever round-trips through NBT.</p>
 */
public final class FakeAirLightBlockEntity extends BlockEntity {

    private static final String TAG_HOST = "host";

    @Nullable
    private BlockPos host;

    public FakeAirLightBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.FAKE_AIR_LIGHT.get(), pos, state);
    }

    public void setHost(BlockPos host) {
        this.host = host;
        setChanged();
    }

    @Nullable
    public BlockPos host() {
        return host;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FakeAirLightBlockEntity light) {
        if (light.host == null) {
            return;
        }
        if (!level.isLoaded(light.host) || level.getBlockEntity(light.host) == null) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (host != null) {
            tag.putLong(TAG_HOST, host.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        host = tag.contains(TAG_HOST) ? BlockPos.of(tag.getLong(TAG_HOST)) : null;
    }
}
