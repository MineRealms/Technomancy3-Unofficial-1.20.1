package theflogat.technomancy.common.tiles.machines;

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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaPush;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.machines.CondenserBalance;
import theflogat.technomancy.common.machines.CondenserProduction;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.compat.gtceu.EuTier;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The energy condenser: it spends Forge Energy to make essentia, which is the essentia
 * dynamo run backwards.
 *
 * <p>Despite the name it condenses nothing out of the world — no aura node, no vis, no
 * ambient anything. The only input is energy, and the only output is {@code potentia}, fixed
 * as in the 1.7.10 original. It is an energy sink with a useful by-product, not a step in a
 * power chain; {@link CondenserBalance} is what keeps it that way.</p>
 *
 * <p>Essentia leaves through faces the player has switched on by sneak-clicking them, either
 * because the condenser pushes it into an adjacent endpoint or because a tube pulls it out.
 * Nothing goes in: {@link #canInputFrom} is {@code false} on every face and
 * {@link #addEssentia} always accepts zero, which agree with each other. The original claimed
 * through {@code doesContainerAccept} that it took {@code ENERGY} and then silently discarded
 * everything handed to it (defect A-10).</p>
 */
public final class EnergyCondenserBlockEntity extends BlockEntity
        implements EssentiaTransport, AspectContainerView {

    /** {@code TileCondenser.aspect = Aspect.ENERGY}. Fixed; see {@link CondenserBalance}. */
    public static final AspectId POTENTIA = AspectId.parse(CondenserBalance.ASPECT);

    /** {@code TileCondenser()} passed {@code RedstoneSet.LOW}: it runs without a signal. */
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.LOW;

    /**
     * EU input rating. The voltage is the machine's own maximum draw expressed as one ampere
     * at the default 4 Q per EU ({@value CondenserBalance#MAX_RATE_Q_PER_TICK} Q/t = 2048 EU/t
     * = EV), so a native EU feed can reach exactly the same ceiling as an FE feed; rating it
     * lower would make the native protocol strictly worse than GTCEu's own FE wrapper. Two
     * amperes so a cable running below that voltage is not capped to a fraction of it — the
     * shared per-tick Q budget stays the real limit either way.
     */
    private static final long EU_INPUT_VOLTAGE = EuTier.EV.voltage();
    private static final long EU_INPUT_AMPS = 2;

    /**
     * Ticks between pushes. A TC4R tube moves one unit every five ticks, so scanning the
     * neighbours faster only repeats work; the condenser makes a unit every 25 ticks at full
     * rate, so it cannot fall behind either.
     */
    private static final int PUSH_INTERVAL = 5;

    /** Energy sync granularity: a packet goes out when the buffer crosses a 5% step. */
    private static final int ENERGY_SYNC_STEPS = 20;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ESSENTIA = "Essentia";
    private static final String TAG_PRODUCTION = "Production";
    private static final String TAG_REDSTONE_MODE = "redstone_mode";
    private static final String TAG_REDSTONE_MODIFIED = "redstone_modified";

    private final MachineEnergy energy;
    private final EssentiaStore store =
            new EssentiaStore(EssentiaLimits.jar(CondenserBalance.ESSENTIA_CAPACITY));
    private final CondenserProduction production;

    private RedstoneMode redstone = DEFAULT_REDSTONE;
    /** Whether a player ever programmed the mode; decides if a programming item is given back. */
    private boolean redstoneModified;

    private int ticks;
    private boolean working;
    private int syncedAmount = -1;
    private long syncedEnergyStep = -1;

    public EnergyCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ENERGY_CONDENSER.get(), pos, state);
        // Six faces in, none out. The original was an IEnergyHandler with canConnectEnergy
        // true everywhere, which also left extractEnergy open, so any conduit could drain the
        // buffer it had spent so long filling (defect A-11).
        energy = new MachineEnergy(this, limits(), EnergyPorts.consumer(EnergyPorts.ALL));
        store.setListener(this::setChanged);
        production = new CondenserProduction(
                TechnomancyConfig.CONDENSER_COST.get(), CondenserBalance.MAX_RATE_Q_PER_TICK);
    }

    private static EnergyLimits limits() {
        // No extraction budget at all: this is the structural half of "no output face".
        return EnergyLimits.fe(CondenserBalance.ENERGY_CAPACITY_Q, CondenserBalance.ENERGY_CAPACITY_Q, 0)
                .withEuInput(EU_INPUT_VOLTAGE, EU_INPUT_AMPS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            EnergyCondenserBlockEntity condenser) {
        condenser.ticks++;
        condenser.convert(level, pos);
        // Not gated on redstone, on purpose: switching a condenser off stops it making
        // essentia, it does not make it hold hostage what it already made. Unlike the dynamo's
        // intake (defect A-18) this takes nothing away from anyone downstream.
        if (condenser.ticks % PUSH_INTERVAL == 0 && condenser.store.total() > 0) {
            condenser.pushToNeighbours(level, state);
        }
        condenser.syncIfDisplayChanged();
    }

    private void convert(Level level, BlockPos pos) {
        boolean allowed = redstone.canRun(level.hasNeighborSignal(pos));
        int space = store.space(POTENTIA);
        working = allowed && space > 0 && energy.ledger().stored() > 0;
        if (!allowed) {
            return;
        }
        int produced = production.tick(energy.ledger(), space);
        if (produced > 0) {
            int stored = store.add(POTENTIA, produced, false);
            if (stored != produced) {
                // Unreachable: tick() only produces when space was reported. Checked because
                // the energy is already gone, so a miscount here would destroy it silently.
                Technomancy.LOGGER.error("Condenser at {} made {} {} but could only store {}",
                        pos, produced, POTENTIA, stored);
            }
        }
    }

    /**
     * Offers the buffer to every switched-on face, debiting exactly what each neighbour took.
     *
     * <p>This is the fix for the defect that gives this block its reputation: 1.7.10 wrote
     * {@code amount = te.addEssentia(aspect, amount, dir)}, treating "accepted" as "left
     * over", so a neighbour that took everything duplicated the essentia and one that took
     * nothing destroyed it (defect A-9). {@link EssentiaPush} carries the conservation rule
     * and is unit tested against receivers that accept all, some and none of an offer.</p>
     */
    private void pushToNeighbours(Level level, BlockState state) {
        if (!known(POTENTIA)) {
            return;
        }
        for (Direction face : Direction.values()) {
            if (store.total() <= 0) {
                return;
            }
            if (!EnergyCondenserBlock.outputs(state, face)) {
                continue;
            }
            EssentiaTransport neighbour =
                    ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, face);
            // Faces are viewed from the endpoint towards its neighbour, so the receiving side
            // is addressed by the opposite face, never by ours.
            Direction inbound = face.getOpposite();
            if (neighbour == null || !neighbour.canInputFrom(inbound)) {
                continue;
            }
            EssentiaPush.push(store, POTENTIA, store.amount(POTENTIA), (aspect, offered) ->
                    EssentiaApi.add(level, neighbour, aspect, offered, inbound, EssentiaTransferMode.EXECUTE));
        }
    }

    // ---- state accessors ----

    public EssentiaStore store() {
        return store;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public int amount() {
        return store.total();
    }

    /** Whether the machine converted energy on the last server tick. */
    public boolean isWorking() {
        return working;
    }

    /** Fraction of the unit currently being made that is already paid for. */
    public float progress() {
        return production.progressFraction();
    }

    /** Q already spent on the unit being made; the rest of the books' third column. */
    public long unfinishedQ() {
        return production.unfinishedQ();
    }

    /** Q one unit of essentia costs, as configured when this machine was created. */
    public long costQ() {
        return production.costQ();
    }

    public RedstoneMode redstoneMode() {
        return redstone;
    }

    public boolean isRedstoneModified() {
        return redstoneModified;
    }

    /**
     * Programs the redstone mode.
     *
     * @return the mode that was in effect before, or {@code null} if nothing changed
     */
    @Nullable
    public RedstoneMode setRedstoneMode(RedstoneMode mode) {
        if (mode == redstone) {
            return null;
        }
        RedstoneMode previous = redstone;
        redstone = mode;
        redstoneModified = true;
        changedAndSync();
        return previous;
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
        return EnergyCondenserBlock.outputs(getBlockState(), face);
    }

    /** Never. The buffer is filled from energy only, and {@link #addEssentia} agrees. */
    @Override
    public boolean canInputFrom(Direction face) {
        return false;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return isConnectable(face);
    }

    /** The condenser wants nothing, so it advertises no type and no pressure. */
    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return 0;
    }

    /** Zero, so any tube with the least suction can drain it. Same as the original. */
    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canOutputTo(face) || amount <= 0 || !POTENTIA.equals(aspect)) {
            return 0;
        }
        // The real amount taken, in [0, amount]. The original returned the full request or
        // zero because it forwarded a boolean, which is not what this contract asks for.
        return store.take(aspect, amount, !mode.executes());
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return known(POTENTIA) ? POTENTIA : null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return isConnectable(face) ? store.amount(POTENTIA) : 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    private static boolean known(AspectId aspect) {
        return AspectApi.registry().get(aspect).isPresent();
    }

    // ---- capabilities ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> view = energy.getCapability(cap, side);
        return view.isPresent() ? view : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
    }

    // ---- persistence and sync ----

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            syncedAmount = store.total();
            syncedEnergyStep = energyStep();
        }
    }

    /**
     * Sends an update only when something a client can see has actually moved. The original
     * called {@code markBlockForUpdate} unconditionally on every tick and on every energy call
     * whether or not anything changed, which is a full block-entity packet per machine per
     * tick to every observer (defect A-6, A-11).
     */
    private void syncIfDisplayChanged() {
        if (store.total() != syncedAmount || energyStep() != syncedEnergyStep) {
            changedAndSync();
        }
    }

    private long energyStep() {
        long capacity = Math.max(1, energy.ledger().capacity());
        return energy.ledger().stored() * ENERGY_SYNC_STEPS / capacity;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.load(tag.getCompound(TAG_ENERGY));
        if (store.load(tag.getCompound(TAG_ESSENTIA))) {
            Technomancy.LOGGER.warn("Condenser at {} could not restore its contents verbatim", worldPosition);
        }
        if (production.load(tag.getCompound(TAG_PRODUCTION))) {
            Technomancy.LOGGER.warn("Condenser at {} could not restore its progress verbatim", worldPosition);
        }
        redstone = RedstoneMode.byId(tag.getString(TAG_REDSTONE_MODE), DEFAULT_REDSTONE);
        redstoneModified = tag.getBoolean(TAG_REDSTONE_MODIFIED);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ENERGY, energy.save());
        tag.put(TAG_ESSENTIA, store.save());
        // Partial progress is saved, so stopping a condenser mid-cycle parks the energy it has
        // already spent instead of throwing it away.
        tag.put(TAG_PRODUCTION, production.save());
        tag.putString(TAG_REDSTONE_MODE, redstone.id());
        tag.putBoolean(TAG_REDSTONE_MODIFIED, redstoneModified);
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
