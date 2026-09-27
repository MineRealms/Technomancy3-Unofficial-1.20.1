package theflogat.technomancy.common.tiles.essentia;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaJarView;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.List;
import java.util.Set;
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
 * The quantum jar: one aspect, ten times a warded jar's capacity, and suction that grows with
 * what it already holds.
 *
 * <p>Ported from {@code TileEssentiaContainer}, which inherited most of its behaviour from
 * Thaumcraft 4's {@code TileJarFillable}. TC4R's {@code WardedJarBlockEntity} is the
 * reference for everything inherited, so this class follows it closely and keeps only the
 * two things the original actually overrode: the capacity and the suction formula.</p>
 */
public final class QuantumJarBlockEntity extends BlockEntity
        implements EssentiaTransport, EssentiaSource, EssentiaJarView {

    /** {@code TileEssentiaContainer.max}: ten warded jars in one block. */
    public static final int CAPACITY = 640;

    /**
     * {@code (filter != null ? 64 : 48) + amount / 50}.
     *
     * <p>The original was {@code 56} labelled, chosen against Thaumcraft 4's own 48/64 jar.
     * TC4R lowered the unlabelled warded jar to 32 but kept labelled at 64, which would have
     * left this block weaker than a plain labelled jar until it was a third full — directly
     * contradicting its own research text ("a higher suction rate than unlabeled warded
     * jars"). Raising the labelled base to 64 makes both tiers at least match the reference
     * jar, and the per-50 bonus is what then puts it ahead. Recorded as a deliberate
     * deviation.</p>
     */
    private static final EssentiaSuction SUCTION = new EssentiaSuction(64, 48, 50);

    private static final EssentiaPorts PORTS = EssentiaPorts.TOP;
    /** TC4R's jar pulls one unit every five ticks; matching it keeps tube throughput familiar. */
    private static final int PULL_INTERVAL = 5;

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(CAPACITY));
    private int ticks;

    public QuantumJarBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.QUANTUM_JAR.get(), pos, state);
        store.setListener(this::changedAndSync);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuantumJarBlockEntity jar) {
        jar.ticks++;
        if (jar.ticks % PULL_INTERVAL == 0 && jar.store.total() < CAPACITY) {
            jar.pullFromAbove();
        }
    }

    /**
     * Whether the label contradicts the contents.
     *
     * <p>Only reachable from a save written by an older build, since the store now refuses
     * such a filter; a jar in this state can never accept anything again, so it must stop
     * advertising suction instead of hoarding a demand it cannot satisfy.</p>
     */
    private boolean labelContradictsContents() {
        AspectId label = filter();
        return label != null && !store.isEmpty() && !label.equals(store.dominantAspect());
    }

    public EssentiaStore store() {
        return store;
    }

    @Nullable
    public AspectId aspect() {
        return store.dominantAspect();
    }

    @Nullable
    public AspectId filter() {
        Set<AspectId> filters = store.filters();
        return filters.isEmpty() ? null : filters.iterator().next();
    }

    public int amount() {
        return store.total();
    }

    /**
     * Sets the label. An empty jar adopts the aspect straight away so it advertises typed
     * suction immediately, which is what makes labelling useful in the first place.
     *
     * @return {@code true} if the label changed
     */
    public boolean setFilter(AspectId selected) {
        if (!known(selected)) {
            return false;
        }
        return store.setFilters(List.of(selected));
    }

    /**
     * Removes the label but keeps the remembered aspect, which is what TC4's jar does and
     * TC4R preserves on purpose: an emptied labelled jar stays typed until it is cleared by
     * hand.
     *
     * @return {@code true} if there was a label to remove
     */
    public boolean clearFilter() {
        return store.clearFilters();
    }

    /** Sneak-clearing empties the jar. */
    public void clearContents() {
        store.clearContents();
    }

    /**
     * Fully resets the jar, label included.
     *
     * <p>The original cleared only the amount and left the aspect behind, so the jar read as
     * empty while still reporting a type to tubes and the goggles. Clearing both is the fix
     * for that (defect A-2).</p>
     */
    public void reset() {
        boolean changed = !store.isEmpty() || !store.filters().isEmpty();
        store.clearContents();
        store.clearFilters();
        if (changed) {
            changedAndSync();
        }
    }

    public int comparatorOutput() {
        int amount = store.total();
        return (int) Math.floor((float) amount / CAPACITY * 14.0F) + (amount > 0 ? 1 : 0);
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
    public Set<AspectId> visibleAspectFilters() {
        return store.filters();
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
        AspectId wanted = filter() != null ? filter() : store.dominantAspect();
        return known(wanted) ? wanted : null;
    }

    @Override
    public int suctionAmount(Direction face) {
        if (!PORTS.canInputFrom(face)) {
            return 0;
        }
        // A jar that cannot actually accept anything must not outbid containers that can -
        // either because its remembered type is no longer a registered aspect, or because a
        // label left over from an older save contradicts what it holds.
        if (suctionType(face) == null && (filter() != null || store.dominantAspect() != null)) {
            return 0;
        }
        if (labelContradictsContents()) {
            return 0;
        }
        return SUCTION.amount(filter() != null, store.total() >= CAPACITY, false, store.total());
    }

    @Override
    public int minimumSuction() {
        return SUCTION.minimum(filter() != null, store.total());
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!PORTS.canOutputTo(face)) {
            return 0;
        }
        return extractEssentia(aspect, amount, mode);
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!PORTS.canInputFrom(face) || !known(aspect)) {
            return 0;
        }
        return store.add(aspect, amount, !mode.executes());
    }

    /** All-or-nothing, as remote drains require: either the whole request or zero. */
    @Override
    public int extractEssentia(AspectId aspect, int amount, EssentiaTransferMode mode) {
        if (!known(aspect)) {
            return 0;
        }
        return store.takeExact(aspect, amount, !mode.executes()) ? amount : 0;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        AspectId held = store.dominantAspect();
        return known(held) ? held : null;
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

    // ---- active pull, following WardedJarBlockEntity.fillJar ----

    private void pullFromAbove() {
        if (level == null) {
            return;
        }
        EssentiaTransport above =
                ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, Direction.UP);
        if (above == null || !above.canOutputTo(Direction.DOWN)) {
            return;
        }
        int mySuction = suctionAmount(Direction.UP);
        int theirSuction = above.suctionAmount(Direction.DOWN);

        AspectId selected = filter();
        if (selected == null) {
            selected = store.dominantAspect();
        }
        if (selected == null && above.essentiaAmount(Direction.DOWN) > 0
                && EssentiaSuction.canDiscover(mySuction, theirSuction, above.minimumSuction())) {
            // Nothing bound yet, so adopt whatever is on offer - the only branch TC4 gates on
            // the giver's minimum suction.
            selected = above.essentiaType(Direction.DOWN);
        }
        if (selected == null || !known(selected) || !EssentiaSuction.canTake(mySuction, theirSuction)) {
            return;
        }
        /*
         * EssentiaApi.take with EXECUTE is irreversible: once it returns, the unit is gone
         * from upstream whether or not this store accepts it. So ask the store first. TC4R's
         * own jar skips this check because its refusing state is unreachable - a filter that
         * contradicts the contents cannot be set there - but a guard costs nothing and means
         * a future state we have not thought of cannot silently delete essentia.
         */
        if (store.add(selected, 1, true) <= 0) {
            return;
        }
        int taken = EssentiaApi.take(level, above, selected, 1, Direction.DOWN, EssentiaTransferMode.EXECUTE);
        if (taken > 0) {
            int accepted = store.add(selected, taken, false);
            if (accepted < taken) {
                Technomancy.LOGGER.warn("Quantum jar at {} lost {} {} it had already pulled",
                        worldPosition, taken - accepted, selected);
            }
        }
    }

    private static boolean known(@Nullable AspectId aspect) {
        return aspect != null && AspectApi.registry().get(aspect).isPresent();
    }

    // ---- persistence and sync ----

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (store.load(tag.getCompound("Essentia"))) {
            Technomancy.LOGGER.warn("Quantum jar at {} could not restore its contents verbatim", worldPosition);
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
