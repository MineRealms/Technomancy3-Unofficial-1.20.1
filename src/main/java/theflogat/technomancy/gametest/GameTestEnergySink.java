package theflogat.technomancy.gametest;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import theflogat.technomancy.Technomancy;

/**
 * A real Forge Energy consumer that can be placed in a GameTest world.
 *
 * <p>Nothing in vanilla or in Thaumcraft accepts Forge Energy, and the port's own blocks are
 * generators, so proving that a dynamo actually delivers needs a receiver built for the purpose.
 * This one hangs an {@link IEnergyStorage} on a plain barrel through
 * {@link AttachCapabilitiesEvent}, which means the dynamo reaches it by exactly the capability
 * lookup it would use on any other mod's machine - no test hook inside the dynamo itself.</p>
 *
 * <p>The listener is registered from test code only and attaches at nothing but the positions a
 * test asked for. This whole package is excluded from the release JAR, so no shipped class knows
 * it exists.</p>
 */
final class GameTestEnergySink implements IEnergyStorage, ICapabilityProvider {

    private static final ResourceLocation KEY =
            new ResourceLocation(Technomancy.MOD_ID, "gametest_energy_sink");
    private static final Map<BlockPos, GameTestEnergySink> REQUESTED = new HashMap<>();
    private static boolean listening;

    private final LazyOptional<IEnergyStorage> view = LazyOptional.of(() -> this);
    private final int capacity;
    private final int maxReceive;
    private int stored;
    private long everReceived;

    private GameTestEnergySink(int capacity, int maxReceive) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
    }

    /**
     * Places a barrel carrying a fresh sink.
     *
     * @param capacity   total room, so a test can also drive the "receiver is full" case
     * @param maxReceive per-call ceiling, so a test can make the receiver the bottleneck
     */
    static synchronized GameTestEnergySink placeAt(ServerLevel level, BlockPos pos, int capacity,
            int maxReceive) {
        if (!listening) {
            MinecraftForge.EVENT_BUS.<AttachCapabilitiesEvent<BlockEntity>, BlockEntity>addGenericListener(
                    BlockEntity.class, GameTestEnergySink::attach);
            listening = true;
        }
        GameTestEnergySink sink = new GameTestEnergySink(capacity, maxReceive);
        // Recorded before the block is placed, because the capability is attached while the
        // block entity is being built.
        REQUESTED.put(pos.immutable(), sink);
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        BlockEntity host = level.getBlockEntity(pos);
        if (host == null) {
            throw new IllegalStateException("no block entity to host the energy sink at " + pos);
        }
        if (host.getCapability(ForgeCapabilities.ENERGY).orElse(null) != sink) {
            throw new IllegalStateException("the energy sink was not attached at " + pos
                    + "; AttachCapabilitiesEvent did not reach it");
        }
        return sink;
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        GameTestEnergySink sink = REQUESTED.get(event.getObject().getBlockPos());
        if (sink != null) {
            event.addCapability(KEY, sink);
        }
    }

    /** Everything this sink has ever been given, which is what a conservation check needs. */
    long everReceived() {
        return everReceived;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.min(Math.min(maxReceive, this.maxReceive), capacity - stored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            stored += accepted;
            everReceived += accepted;
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? view.cast() : LazyOptional.empty();
    }
}
