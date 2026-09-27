package theflogat.technomancy.common.tiles.essentia;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaPorts;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The essentia reservoir: a single-aspect store open on all six faces that actively drains
 * every neighbour it out-sucks.
 *
 * <p>Ported from {@code TileEssentiaReservoir} (256 units, suction 128, minimum suction 0).
 * Three legacy defects are not reproduced: {@code takeEssentia} returned {@code 0} on success
 * and the full request on failure (inverted); the pull loop took the neighbour's aspect first
 * and then discarded whatever {@code addToContainer} refused, deleting essentia whenever the
 * reservoir already held another aspect; and a full reservoir kept advertising suction 128, so
 * tubes kept offering it essentia it could not take. It is deliberately not an
 * {@code EssentiaSource}: the original was only an {@code IAspectContainer}, which TC4's
 * infusion drain never looked at.</p>
 */
public final class EssentiaReservoirBlockEntity extends BlockEntity
        implements EssentiaTransport, AspectContainerView {

    /** {@code TileEssentiaReservoir.maxAmount}. */
    public static final int CAPACITY = 256;
    /** {@code getSuctionAmount} returned a flat 128 on every face. */
    public static final int SUCTION = 128;

    private static final EssentiaPorts PORTS = EssentiaPorts.ALL;
    /** Client updates are coalesced; pulling from six faces could otherwise send one per tick. */
    private static final int SYNC_INTERVAL = 10;

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(CAPACITY));
    private int ticks;
    private boolean syncPending;

    public EssentiaReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ESSENTIA_RESERVOIR.get(), pos, state);
        store.setListener(this::changed);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaReservoirBlockEntity reservoir) {
        reservoir.ticks++;
        if (reservoir.store.total() < CAPACITY) {
            reservoir.pullFromNeighbours(level);
        }
        if (reservoir.syncPending && reservoir.ticks % SYNC_INTERVAL == 0) {
            reservoir.syncPending = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public EssentiaStore store() {
        return store;
    }

    @Nullable
    public AspectId aspect() {
        return store.dominantAspect();
    }

    public int amount() {
        return store.total();
    }

    /**
     * One unit per face per tick, as the original's {@code fill()}.
     *
     * <p>The original checked the giver's minimum suction on every pull, not only when it had
     * to adopt a new aspect as TC4's jar does, so {@link EssentiaSuction#canDiscover} is the
     * rule for both branches here.</p>
     */
    private void pullFromNeighbours(Level level) {
        for (Direction face : Direction.values()) {
            if (store.total() >= CAPACITY) {
                return;
            }
            EssentiaTransport neighbour = ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, face);
            Direction theirs = face.getOpposite();
            if (neighbour == null || !neighbour.canOutputTo(theirs)) {
                continue;
            }
            int mine = suctionAmount(face);
            if (!EssentiaSuction.canDiscover(mine, neighbour.suctionAmount(theirs), neighbour.minimumSuction())) {
                continue;
            }
            AspectId wanted = store.dominantAspect();
            if (wanted == null) {
                wanted = neighbour.essentiaType(theirs);
                if (wanted == null) {
                    wanted = neighbour.extractableAspect(theirs);
                }
            }
            if (!known(wanted) || neighbour.availableEssentia(wanted, theirs) <= 0) {
                continue;
            }
            // Ask the store before the irreversible take, never after it (see QuantumJarBlockEntity).
            if (store.add(wanted, 1, true) <= 0) {
                continue;
            }
            int taken = EssentiaApi.take(level, neighbour, wanted, 1, theirs, EssentiaTransferMode.EXECUTE);
            if (taken > 0) {
                int accepted = store.add(wanted, taken, false);
                if (accepted < taken) {
                    Technomancy.LOGGER.error("Essentia reservoir at {} lost {} {} it had already pulled",
                            worldPosition, taken - accepted, wanted);
                }
            }
        }
    }

    // ---- essentia views ----

    @Override
    public AspectAmounts visibleAspects() {
        return store.visibleAspects();
    }

    @Override
    public List<AspectId> visibleAspectOrder() {
        return store.visibleAspectOrder();
    }

    @Override
    public boolean isConnectable(Direction face) {
        return PORTS.isConnectable(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return PORTS.canInputFrom(face);
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return PORTS.canOutputTo(face);
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        AspectId held = store.dominantAspect();
        return known(held) ? held : null;
    }

    /** 128 until full, then 0: a full store must not keep bidding for what it cannot hold. */
    @Override
    public int suctionAmount(Direction face) {
        if (!PORTS.canInputFrom(face) || store.total() >= CAPACITY) {
            return 0;
        }
        // Holding an aspect the registry no longer knows: it can never accept anything again.
        if (!store.isEmpty() && suctionType(face) == null) {
            return 0;
        }
        return SUCTION;
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    /** The amount actually removed, in {@code [0, amount]}; the original returned it inverted. */
    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!PORTS.canOutputTo(face) || !known(aspect)) {
            return 0;
        }
        return store.take(aspect, amount, !mode.executes());
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!PORTS.canInputFrom(face) || !known(aspect)) {
            return 0;
        }
        return store.add(aspect, amount, !mode.executes());
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return suctionType(face);
    }

    @Override
    public int essentiaAmount(Direction face) {
        return essentiaType(face) == null ? 0 : store.total();
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return PORTS.canOutputTo(face) ? essentiaType(face) : null;
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return PORTS.canOutputTo(face) && known(aspect) ? store.amount(aspect) : 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    private static boolean known(@Nullable AspectId aspect) {
        return aspect != null && AspectApi.contains(aspect);
    }

    // ---- persistence and sync ----

    private void changed() {
        setChanged();
        syncPending = true;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (store.load(tag.getCompound("Essentia"))) {
            Technomancy.LOGGER.warn("Essentia reservoir at {} could not restore its contents verbatim", worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Essentia", store.save());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
