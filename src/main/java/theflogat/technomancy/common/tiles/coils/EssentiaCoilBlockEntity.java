package theflogat.technomancy.common.tiles.coils;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSourceRef;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.coils.CoilLink;
import theflogat.technomancy.common.coils.CoupleType;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The essentia coil ({@code teslaCoil}): wireless essentia from its linked stores into the block
 * it stands on. Ported from {@code TileEssentiaTransmitter}.
 *
 * <p>It works in both directions, exactly as the original did, but without the original's
 * hard-coded class list (engineering guide 9.3):</p>
 * <ul>
 *   <li><b>Push</b> - every tick it offers one unit per linked store to the neighbour it faces,
 *       through {@link EssentiaTransport}. The original checked its neighbour against
 *       {@code TileArcaneBoreBase}, two lamp classes, {@code TileThaumatorium} and
 *       {@code TileTubeBuffer} by name and pushed only into the rest.</li>
 *   <li><b>Pull</b> - it is itself an output-only {@link EssentiaTransport} on the face it
 *       stands on, so machines that fetch essentia for themselves (the Thaumatorium, the Arcane
 *       Bore, an Arcane Lamp, a tube) take from it and it fetches that aspect from its links.
 *       This is the original's {@code takeEssentia}, which it only answered for the named
 *       special blocks.</li>
 * </ul>
 *
 * <p>It is deliberately <b>not</b> an {@link EssentiaSource}: a remote drain (an infusion altar,
 * an essentia mirror) may not reach through a coil into its links, or the coil would silently
 * multiply everyone else's range. The original was not a TC4 {@code IAspectContainer} either, so
 * this matches it.</p>
 *
 * <p>Links are drained through {@link EssentiaApi}, which re-resolves the position on every call,
 * so no block entity is ever held across ticks.</p>
 */
public final class EssentiaCoilBlockEntity extends CoilBlockEntity implements EssentiaTransport {

    private static final String TAG_FILTER = "Filter";

    /** Units offered per linked store per tick; the original's {@code addToContainer(aspect, 1)}. */
    private static final int PUSH_PER_LINK = 1;

    @Nullable
    private AspectId filter;

    public EssentiaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ESSENTIA_COIL.get(), pos, state);
    }

    @Override
    public CoupleType coupleType() {
        return CoupleType.ESSENTIA;
    }

    @Nullable
    public AspectId filter() {
        return filter;
    }

    /** The filter, but only while it names an aspect this game actually has. */
    @Nullable
    private AspectId activeFilter() {
        return known(filter) ? filter : null;
    }

    /** @return whether the filter changed */
    public boolean setFilter(@Nullable AspectId selected) {
        AspectId next = known(selected) ? selected : null;
        if (Objects.equals(next, filter)) {
            return false;
        }
        filter = next;
        syncToClients();
        return true;
    }

    private static boolean known(@Nullable AspectId aspect) {
        return aspect != null && AspectApi.registry().get(aspect).isPresent();
    }

    // ---- links ----

    /**
     * A link is usable if it can be drained: either an all-or-nothing {@link EssentiaSource}
     * (jars, reservoirs - what the original's {@code IAspectContainer} sources really were), or
     * a directional {@link EssentiaTransport} willing to output through the linked face.
     */
    @Override
    protected boolean isValidSource(Level level, BlockPos pos, @Nullable Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof CoilBlockEntity) {
            return false;
        }
        return be instanceof EssentiaSource
                || be instanceof EssentiaTransport transport && face != null && transport.canOutputTo(face);
    }

    /** Aspects a link could currently give up, best effort; an empty list means "nothing now". */
    private List<AspectId> supply(Level level, CoilLink link) {
        AspectId only = activeFilter();
        if (only != null) {
            return List.of(only);
        }
        if (filter != null) {
            // A filter naming an aspect this game does not have transfers nothing at all, rather
            // than quietly becoming "any aspect".
            return List.of();
        }
        BlockEntity be = level.getBlockEntity(link.pos());
        List<AspectId> candidates = new ArrayList<>(2);
        if (be instanceof AspectContainerView view) {
            // Jars and reservoirs; the original read IAspectContainer.getAspects() the same way.
            view.visibleAspects().amounts().forEach((aspect, amount) -> {
                if (amount > 0 && known(aspect)) {
                    candidates.add(aspect);
                }
            });
        }
        if (candidates.isEmpty() && be instanceof EssentiaTransport transport && link.face() != null) {
            AspectId offered = transport.extractableAspect(link.face());
            if (offered == null) {
                offered = transport.essentiaType(link.face());
            }
            if (known(offered)) {
                candidates.add(offered);
            }
        }
        return candidates;
    }

    /**
     * Takes up to {@code amount} of one aspect out of one link.
     *
     * <p>{@link EssentiaApi#extract(Level, EssentiaSourceRef, AspectId, int, EssentiaTransferMode)}
     * is all-or-nothing, so a store that cannot serve the whole request is asked for a single
     * unit instead - which is what every consumer in practice wants and all the original ever
     * moved.</p>
     *
     * @return units actually taken (or, in simulation, that would be taken)
     */
    private int drain(Level level, CoilLink link, AspectId aspect, int amount, EssentiaTransferMode mode) {
        if (amount <= 0 || !known(aspect)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(link.pos());
        if (be instanceof EssentiaSource) {
            EssentiaSourceRef ref = new EssentiaSourceRef(level.dimension(), link.pos());
            int moved = EssentiaApi.extract(level, ref, aspect, amount, mode);
            if (moved == 0 && amount > 1) {
                moved = EssentiaApi.extract(level, ref, aspect, 1, mode);
            }
            return moved;
        }
        if (be instanceof EssentiaTransport transport && link.face() != null) {
            return EssentiaApi.take(level, transport, aspect, amount, link.face(), mode);
        }
        return 0;
    }

    // ---- push into the block the coil stands on ----

    @Override
    protected void drawFrom(Level level, CoilLink link, BlockPos targetPos, Direction facing) {
        EssentiaTransport target = ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, facing);
        if (target == null) {
            // Nothing to push into. A machine that fetches for itself uses takeEssentia instead.
            return;
        }
        Direction targetFace = facing.getOpposite();
        for (AspectId aspect : supply(level, link)) {
            /*
             * Simulate both ends before touching either. Draining first and then discovering the
             * target is full would destroy essentia; adding first and then failing to drain would
             * create it. This is the same ordering rule QuantumJarBlockEntity follows, and the
             * inverse of the original, which called addToContainer and then tried to undo it.
             */
            if (EssentiaApi.add(level, target, aspect, PUSH_PER_LINK, targetFace, EssentiaTransferMode.SIMULATE) < PUSH_PER_LINK) {
                continue;
            }
            if (drain(level, link, aspect, PUSH_PER_LINK, EssentiaTransferMode.SIMULATE) < PUSH_PER_LINK) {
                continue;
            }
            int taken = drain(level, link, aspect, PUSH_PER_LINK, EssentiaTransferMode.EXECUTE);
            if (taken <= 0) {
                continue;
            }
            int accepted = EssentiaApi.add(level, target, aspect, taken, targetFace, EssentiaTransferMode.EXECUTE);
            if (accepted < taken) {
                giveBack(level, link, aspect, taken - accepted);
            }
            return;
        }
    }

    /** Last resort when a target accepted less than its own simulation promised. */
    private void giveBack(Level level, CoilLink link, AspectId aspect, int amount) {
        int returned = 0;
        if (level.getBlockEntity(link.pos()) instanceof EssentiaTransport transport && link.face() != null) {
            returned = EssentiaApi.add(level, transport, aspect, amount, link.face(), EssentiaTransferMode.EXECUTE);
        }
        if (returned < amount) {
            Technomancy.LOGGER.warn("Essentia coil at {} lost {} {} that {} accepted in simulation and then refused",
                    worldPosition, amount - returned, aspect, worldPosition.relative(facing()));
        }
    }

    /** The Potency Gem signal: the original's "not full" test on the block being fed. */
    @Override
    protected boolean targetHasRoom(Level level, BlockPos targetPos, Direction facing) {
        EssentiaTransport target = ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, facing);
        if (target == null) {
            return false;
        }
        Direction targetFace = facing.getOpposite();
        AspectId wanted = activeFilter() != null ? activeFilter() : target.suctionType(targetFace);
        if (!known(wanted)) {
            wanted = anySupply(level);
        }
        return known(wanted)
                && EssentiaApi.add(level, target, wanted, 1, targetFace, EssentiaTransferMode.SIMULATE) > 0;
    }

    @Nullable
    private AspectId anySupply(Level level) {
        for (CoilLink link : links.view()) {
            if (!level.isLoaded(link.pos())) {
                continue;
            }
            for (AspectId aspect : supply(level, link)) {
                if (drain(level, link, aspect, 1, EssentiaTransferMode.SIMULATE) > 0) {
                    return aspect;
                }
            }
        }
        return null;
    }

    // ---- pulled from: an output-only transport on the face the coil stands on ----

    /** Whether this coil may serve essentia through {@code face} right now. */
    private boolean serves(Direction face) {
        return level != null && !level.isClientSide && face == facing() && !links.isEmpty() && canRun();
    }

    @Override
    public boolean isConnectable(Direction face) {
        // Answered on both sides and without the run gate, so a tube's connection model and its
        // rendering do not flicker with a redstone signal.
        return face == facing();
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return false;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return isConnectable(face);
    }

    /** A coil wants nothing for itself; it holds nothing. */
    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return 0;
    }

    /** {@code 0} in the original too: any consumer with any suction at all may pull from a coil. */
    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    /**
     * Fetches what a consumer asks for from the links, one after another.
     *
     * <p>The original took the whole request from a single source or nothing; accumulating across
     * links is the contract {@code EssentiaTransport} actually states ("the number actually
     * extracted, between zero and the requested amount") and cannot strand a request that two
     * half-full jars could satisfy together.</p>
     */
    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!serves(face) || !known(aspect) || amount <= 0) {
            return 0;
        }
        Level level = Objects.requireNonNull(this.level);
        int moved = 0;
        for (CoilLink link : links.rotation()) {
            if (moved >= amount) {
                break;
            }
            if (!level.isLoaded(link.pos()) || !isValidSource(level, link.pos(), link.face())) {
                continue;
            }
            moved += drain(level, link, aspect, amount - moved, mode);
        }
        return moved;
    }

    /** What a consumer would see on offer: the filter, or whatever a link can currently give. */
    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        if (!serves(face)) {
            return null;
        }
        Level level = Objects.requireNonNull(this.level);
        AspectId only = activeFilter();
        if (only != null) {
            return drainable(level, only) ? only : null;
        }
        return filter != null ? null : anySupply(level);
    }

    private boolean drainable(Level level, AspectId aspect) {
        for (CoilLink link : links.view()) {
            if (level.isLoaded(link.pos()) && drain(level, link, aspect, 1, EssentiaTransferMode.SIMULATE) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The original returned a constant {@code 1} regardless of what its sources held, so a
     * consumer polling it was told essentia was available even when every jar was empty. This
     * answers what the links can actually give.
     */
    @Override
    public int essentiaAmount(Direction face) {
        AspectId offered = essentiaType(face);
        return offered == null ? 0 : availableEssentia(offered, face);
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return essentiaType(face);
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        if (!serves(face) || !known(aspect)) {
            return 0;
        }
        Level level = Objects.requireNonNull(this.level);
        int available = 0;
        for (CoilLink link : links.view()) {
            if (level.isLoaded(link.pos())) {
                available += drain(level, link, aspect, 1, EssentiaTransferMode.SIMULATE);
            }
        }
        return available;
    }

    /** As in the original: a coil is not drawn as a tube stub. */
    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // ---- persistence and sync ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readClientData(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeClientData(tag);
    }

    @Override
    protected void writeClientData(CompoundTag tag) {
        if (filter != null) {
            tag.putString(TAG_FILTER, filter.toString());
        }
    }

    @Override
    protected void readClientData(CompoundTag tag) {
        filter = null;
        if (!tag.contains(TAG_FILTER)) {
            return;
        }
        String saved = tag.getString(TAG_FILTER);
        try {
            // Kept even when the registry does not know it: a data pack that is not loaded yet
            // must not silently turn a filtered coil into one that passes everything. Only an
            // unparseable string is dropped.
            filter = AspectId.parse(saved);
        } catch (RuntimeException malformed) {
            Technomancy.LOGGER.warn("Essentia coil at {} had an unreadable filter {}", worldPosition, saved);
        }
    }
}
