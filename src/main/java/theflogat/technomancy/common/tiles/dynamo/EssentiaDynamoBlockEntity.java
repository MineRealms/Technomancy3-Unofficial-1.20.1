package theflogat.technomancy.common.tiles.dynamo;

import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import theflogat.technomancy.common.blocks.dynamo.EssentiaDynamoBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaPorts;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.FuelEnvironment;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.compat.gtceu.EuTier;

/**
 * The essentia dynamo: burns essentia drawn from adjacent tubes or jars and emits Forge Energy
 * (and, where a neighbour speaks it, native GT EU) through the single face it faces.
 *
 * <p>Ported from {@code TileEssentiaDynamo} plus {@code TileDynamoBase}. The rates, the buffer,
 * the 64-unit essentia cache and the suction of 128 are the 1.7.10 numbers mapped 1:1 into Q;
 * how much energy one unit of essentia is worth is the one thing that is <em>not</em> 1:1, and
 * that lives in {@code essentiaFuelScale} and the data-driven fuel table.</p>
 *
 * <p>Fuel is tracked as remaining <em>energy</em> in Q rather than as a count of burn ticks.
 * That is what makes the potency gem exactly a throughput change: the gem quadruples both the
 * rate and the units taken per charge, so the Q a unit of essentia yields is identical either
 * way, with no rounding drift between the two cases.</p>
 */
public final class EssentiaDynamoBlockEntity extends BlockEntity implements EssentiaTransport, AspectContainerView, EnergyHolder {

    /** {@code TileEssentiaDynamo.maxAmount}. */
    public static final int ESSENTIA_CAPACITY = 64;
    /** {@code TileDynamoBase.maxEnergy}, 1 RF -> 1 Q. */
    public static final long ENERGY_CAPACITY = 40_000;
    /** {@code TileDynamoBase.maxExtract}. */
    public static final long MAX_OUTPUT = 320;
    /** {@code calcEner()} without the potency gem. */
    public static final long BASE_RATE = 80;
    /** {@code calcEner()} with the potency gem: four times the rate for four times the fuel. */
    public static final long BOOSTED_RATE = 320;
    /** Units of essentia one charge consumes with the potency gem installed. */
    public static final int BOOSTED_UNITS = (int) (BOOSTED_RATE / BASE_RATE);
    /** {@code TileEssentiaDynamo.getSuctionAmount} and {@code getMinimumSuction}. */
    public static final int SUCTION = 128;

    /**
     * The dynamo ignores redstone out of the box.
     *
     * <p>Deliberate deviation: {@code TileDynamoBase()} passed {@code RedstoneSet.HIGH}, so a
     * freshly built dynamo did nothing until it was given a signal, and the research page had to
     * explain that. The three-state control and its three programming items are all still here;
     * only the starting point moved (spec chapter 10, item 3).</p>
     */
    public static final RedstoneMode DEFAULT_REDSTONE_MODE = RedstoneMode.NONE;

    /** Tube-aligned pull rhythm: a plain TC4R tube moves one unit every five ticks anyway. */
    private static final int PULL_INTERVAL = 5;
    /**
     * How far ahead fuel is bought, in ticks of output. {@code TileDynamoBase} refilled while
     * {@code fuel < 32}, i.e. while less than 32 ticks of burning were banked.
     */
    private static final int FUEL_LOOKAHEAD_TICKS = 32;
    /** Energy is re-sent to watchers in twentieths of the buffer, never every tick. */
    private static final long ENERGY_SYNC_STEPS = 20;

    private static final String TAG_VERSION = "v";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ESSENTIA = "Essentia";
    private static final String TAG_FUEL = "Fuel";
    private static final String TAG_BOOST = "Boost";
    private static final int SCHEMA_VERSION = 1;

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(ESSENTIA_CAPACITY));
    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE_MODE);
    /** Whether this dynamo can emit whole EU packets at all; see {@link #energyLimits()}. */
    private final boolean euCapable;

    private EssentiaPorts essentiaPorts;
    private boolean boost;
    /** Energy the essentia already consumed can still produce. */
    private final DynamoFuelBank fuel = new DynamoFuelBank(FUEL_LOOKAHEAD_TICKS);
    private int ticks;

    private int syncedAmount = -1;
    @Nullable
    private AspectId syncedAspect;
    private long syncedEnergyStep = -1;

    public EssentiaDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ESSENTIA_DYNAMO.get(), pos, state);
        // Built per instance, not once per class: the Q-per-EU rate is frozen during common
        // setup, which happens after the block entity type is registered.
        EnergyLimits limits = energyLimits();
        euCapable = limits.emitsEu();
        energy = new MachineEnergy(this, limits, energyPorts(facing(), euCapable));
        essentiaPorts = essentiaPorts(facing());
        store.setListener(this::setChanged);
        // The redstone mode only ever changes through a player action, so it is worth a packet
        // straight away rather than waiting for the tick to notice.
        redstone.setListener(this::changedAndSync);
    }

    /**
     * Energy shape of the dynamo: never accepts, emits at most {@link #MAX_OUTPUT} per tick.
     *
     * <p>The EU rating is derived from the Q budget rather than picked: a packet that does not
     * fit the per-tick budget can never be sent, and the EU protocol refuses an over-voltage
     * packet outright instead of throttling it. At the default four Q per EU that is two
     * amperes of LV, i.e. 256 of the 320 Q/t; a configuration where not even one packet fits
     * leaves EU output off entirely and every neighbour served over Forge Energy.</p>
     */
    private static EnergyLimits energyLimits() {
        EnergyLimits limits = EnergyLimits.fe(ENERGY_CAPACITY, 0, MAX_OUTPUT);
        long voltage = EuTier.LV.voltage();
        long amps = MAX_OUTPUT / (voltage * EnergyUnits.qPerEu());
        return amps > 0 ? limits.withEuOutput(voltage, amps) : limits;
    }

    private static EnergyPorts energyPorts(Direction facing, boolean eu) {
        int mask = EnergyPorts.mask(facing);
        // Output only, and only on the facing side; the 1.7.10 dynamo's receiveEnergy was a
        // hard 0 on every face and canConnectEnergy was true only for facing. A face only
        // claims EU when whole packets can actually leave, or a GT neighbour would be offered
        // an empty native transfer and never fall back to Forge Energy.
        return eu ? EnergyPorts.generator(mask) : new EnergyPorts(0, mask, 0, 0);
    }

    /**
     * Essentia faces: every face except the energy output.
     *
     * <p>{@code canInputFrom} and {@code isConnectable} are the same predicate here. The
     * original returned a constant {@code true} from {@code canInputFrom} while
     * {@code isConnectable} excluded the energy face, so a tube that trusted the former tried
     * to push essentia into the power output (defect A-7).</p>
     */
    private static EssentiaPorts essentiaPorts(Direction facing) {
        Direction[] inputs = new Direction[Direction.values().length - 1];
        int index = 0;
        for (Direction face : Direction.values()) {
            if (face != facing) {
                inputs[index++] = face;
            }
        }
        return EssentiaPorts.consumer(inputs);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaDynamoBlockEntity dynamo) {
        dynamo.ticks++;
        boolean running = dynamo.redstone.canRun(level, pos);
        if (running) {
            dynamo.burn();
            // The pull is inside the redstone gate. The original left fill() outside it, so a
            // dynamo switched off with redstone went on emptying the tube next to it into a
            // buffer it would never burn, starving everything downstream (defect A-18).
            if (dynamo.ticks % PULL_INTERVAL == 0) {
                dynamo.pullEssentia();
            }
        }
        // Delivery is not gated: "off" means stop generating, not hold on to what is already
        // in the buffer. This matches updateAdjacentHandlers() running outside set.canRun.
        dynamo.energy.pushOutput();
        dynamo.updateWorkingState(running);
        dynamo.syncIfChanged();
    }

    // ---- generation ----

    /** Q produced per tick right now. */
    public long ratePerTick() {
        return boost ? BOOSTED_RATE : BASE_RATE;
    }

    /** Units of essentia one charge costs right now. */
    public int unitsPerCharge() {
        return boost ? BOOSTED_UNITS : 1;
    }

    public long fuel() {
        return fuel.banked();
    }

    /**
     * Turns essentia into energy for one tick: buy a charge if the bank is low, then produce.
     *
     * <p>{@link DynamoFuelBank#tick} holds the ordering and the full-buffer rule; everything
     * this method adds is the two accounts the bank is not allowed to touch itself, so an
     * essentia debit and an energy credit can never happen without the other.</p>
     */
    private void burn() {
        AspectId aspect = store.dominantAspect();
        int available = aspect == null ? 0 : store.amount(aspect);
        DynamoFuelBank.Burn burn = fuel.tick(ratePerTick(), unitsPerCharge(), available,
                () -> energyPerUnit(aspect), energy.ledger().space());
        if (burn.unitsConsumed() > 0) {
            store.take(aspect, burn.unitsConsumed(), false);
        }
        if (burn.energyProduced() > 0) {
            long stored = energy.ledger().generate(burn.energyProduced());
            // generate() is bounded by the free capacity the bank was given, so this should be
            // zero; refunding anyway means a future capacity change cannot silently burn fuel.
            fuel.refund(burn.energyProduced() - stored);
        }
    }

    /** Q one unit of the given aspect is worth at this position, right now. */
    private long energyPerUnit(@Nullable AspectId aspect) {
        if (level == null || aspect == null) {
            return 0;
        }
        return EssentiaFuelLoader.table().energyPerUnit(FuelEnvironment.of(level, worldPosition), aspect);
    }

    // ---- active pull, following InfernalFurnaceNozzleBlockEntity ----

    /**
     * Takes at most one unit of essentia from at most one neighbour.
     *
     * <p>One unit per attempt is the original's own limit and is never the bottleneck: a plain
     * TC4R tube holds one unit at a time and moves it once every five ticks.</p>
     */
    private void pullEssentia() {
        if (level == null || store.total() >= ESSENTIA_CAPACITY) {
            return;
        }
        AspectId held = store.dominantAspect();
        for (Direction face : Direction.values()) {
            if (!essentiaPorts.canInputFrom(face)) {
                continue;
            }
            EssentiaTransport neighbour =
                    ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, face);
            if (neighbour == null) {
                continue;
            }
            Direction theirFace = face.getOpposite();
            if (!neighbour.canOutputTo(theirFace)) {
                continue;
            }
            int mine = suctionAmount(face);
            int theirs = neighbour.suctionAmount(theirFace);
            // Strictly more suction than the giver, and at least the giver's own minimum -
            // both checks on every pull, not only while discovering an aspect. TC4's jar
            // applies the minimum only on its discovery branch (see EssentiaSuction.canDiscover),
            // but the dynamo never did, and neither does TC4R's own tube
            // (EssentiaTubeBlockEntity.equalizeWithNeighbours), so a dynamo that skipped it
            // would out-pull the tubes feeding it.
            if (!EssentiaSuction.canTake(mine, theirs) || mine < neighbour.minimumSuction()) {
                continue;
            }
            // Only ever ask for what this dynamo can actually store. The original asked for
            // whatever the neighbour happened to hold and passed it to addToContainer, which
            // refused a second aspect and returned it as "not accepted" - a return value the
            // caller discarded, destroying the unit it had already taken out of the tube.
            AspectId selected = held != null ? held : neighbour.essentiaType(theirFace);
            if (selected == null || !known(selected)) {
                continue;
            }
            if (held == null && neighbour.essentiaAmount(theirFace) <= 0) {
                continue;
            }
            // take(EXECUTE) is irreversible, so confirm the store will keep the unit first. The
            // selection above already makes a refusal unreachable today; this keeps it that way
            // if the store ever gains a filter or a second aspect slot.
            if (store.add(selected, 1, true) <= 0) {
                continue;
            }
            int taken = EssentiaApi.take(level, neighbour, selected, 1, theirFace, EssentiaTransferMode.EXECUTE);
            if (taken <= 0) {
                continue;
            }
            int accepted = store.add(selected, taken, false);
            if (accepted < taken) {
                // take() already removed it upstream, so anything refused here is gone.
                Technomancy.LOGGER.warn("Essentia dynamo at {} lost {} {} it had already pulled",
                        worldPosition, taken - accepted, selected);
            }
            return;
        }
    }

    private static boolean known(@Nullable AspectId aspect) {
        return aspect != null && AspectApi.registry().get(aspect).isPresent();
    }

    // ---- upgrade, facing and redstone ----

    public boolean isBoosted() {
        return boost;
    }

    /**
     * Installs or removes the potency gem.
     *
     * @return {@code true} if the state changed
     */
    public boolean setBoosted(boolean installed) {
        if (boost == installed) {
            return false;
        }
        boost = installed;
        changedAndSync();
        return true;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    public EssentiaStore store() {
        return store;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(EssentiaDynamoBlock.FACING)
                ? state.getValue(EssentiaDynamoBlock.FACING)
                : Direction.UP;
    }

    /**
     * Turns the energy output to the next face, unconditionally.
     *
     * <p>The 1.7.10 wrench walked the six faces and stopped at the first neighbour that would
     * accept energy, refusing to turn at all when there was none - so a dynamo could not be
     * aimed before the machine it was going to feed existed (defect A-19).</p>
     *
     * @return the new output face
     */
    public Direction cycleFacing() {
        Direction next = Direction.from3DDataValue((facing().get3DDataValue() + 1) % Direction.values().length);
        return setFacing(next);
    }

    /** Points the energy output at {@code face} and rebuilds both sets of port rules. */
    public Direction setFacing(Direction face) {
        if (level != null && getBlockState().hasProperty(EssentiaDynamoBlock.FACING)) {
            level.setBlock(worldPosition, getBlockState().setValue(EssentiaDynamoBlock.FACING, face),
                    Block.UPDATE_ALL);
        }
        refreshPorts();
        return facing();
    }

    /**
     * Re-derives the face rules from the block state.
     *
     * <p>Both port sets are keyed on the facing, and handed-out capabilities have to be
     * invalidated when it moves or a neighbour keeps talking to the old output face.</p>
     */
    private void refreshPorts() {
        Direction facing = facing();
        energy.setPorts(energyPorts(facing, euCapable));
        essentiaPorts = essentiaPorts(facing);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshPorts();
    }

    // ---- essentia transport ----

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
        return essentiaPorts.isConnectable(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return essentiaPorts.canInputFrom(face);
    }

    /** A dynamo consumes essentia; nothing ever leaves it. */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    /** {@code null} is a wildcard, so an empty dynamo accepts whatever a tube brings. */
    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        AspectId held = store.dominantAspect();
        return known(held) ? held : null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return essentiaPorts.canInputFrom(face) && store.total() < ESSENTIA_CAPACITY ? SUCTION : 0;
    }

    /**
     * Constant, including when the buffer is full.
     *
     * <p>"How much do I want" and "how hard is it to take from me" are different questions;
     * TC4R's own warded jar keeps them apart the same way (appendix A-1 is withdrawn).</p>
     */
    @Override
    public int minimumSuction() {
        return SUCTION;
    }

    /**
     * Always 0.
     *
     * <p>Deliberate change: the original's {@code takeEssentia} forwarded to
     * {@code takeFromContainer}, so anything calling it directly could pull the dynamo's fuel
     * back out even though {@code canOutputTo} was already false. Refusing here makes the two
     * answers agree.</p>
     */
    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    /** Returns the amount actually accepted, as the contract requires - not the leftover. */
    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!essentiaPorts.canInputFrom(face) || !known(aspect)) {
            return 0;
        }
        return store.add(aspect, amount, !mode.executes());
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        AspectId held = store.dominantAspect();
        return known(held) ? held : null;
    }

    /**
     * Always 0, like every other "what can I get out of this face" answer here.
     *
     * <p>Reporting the fuel cache would be actively harmful, not merely inconsistent: TC4R's
     * buffer tube tests {@code neighbor.essentiaAmount(face) > 0} together with the suction
     * comparison <em>before</em> it looks at {@code canOutputTo}, then transfers whatever
     * {@code EssentiaApi.take} returns - zero, here - and {@code return}s without scanning its
     * remaining faces ({@code EssentiaTubeBlockEntity.fillBuffer}, line 436). A buffer tube
     * beside a dynamo holding cached essentia would stall on it permanently. The contents are
     * still visible to goggles and probes through {@link #visibleAspects()}.</p>
     */
    @Override
    public int essentiaAmount(Direction face) {
        return 0;
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return null;
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ---- energy capabilities ----

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

    // ---- working state and sync ----

    /** True while this dynamo is actually turning fuel into energy. */
    public boolean isWorking() {
        BlockState state = getBlockState();
        return state.hasProperty(EssentiaDynamoBlock.LIT) && state.getValue(EssentiaDynamoBlock.LIT);
    }

    private void updateWorkingState(boolean running) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(EssentiaDynamoBlock.LIT)) {
            return;
        }
        boolean lit = running && fuel.banked() > 0;
        if (state.getValue(EssentiaDynamoBlock.LIT) != lit) {
            // UPDATE_CLIENTS only: the flag is cosmetic and observable state, and a neighbour
            // update here would feed straight back into the redstone mode that produced it.
            level.setBlock(worldPosition, state.setValue(EssentiaDynamoBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Sends a description packet only when something a watcher can see has changed.
     *
     * <p>{@code TileDynamoBase.update()} sent a full block update and re-lit the block every
     * tick, for every dynamo, to every watcher (defect A-6). Energy is quantised to twentieths
     * of the buffer; the aspect and the unit count are exact, because both are displayed
     * exactly.</p>
     */
    private void syncIfChanged() {
        long step = energy.ledger().stored() * ENERGY_SYNC_STEPS / ENERGY_CAPACITY;
        AspectId aspect = store.dominantAspect();
        int amount = store.total();
        if (amount == syncedAmount && step == syncedEnergyStep && Objects.equals(aspect, syncedAspect)) {
            return;
        }
        syncedAmount = amount;
        syncedEnergyStep = step;
        syncedAspect = aspect;
        sendUpdate();
    }

    private void sendUpdate() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** For state that changes outside the tick and is worth a packet immediately. */
    private void changedAndSync() {
        setChanged();
        sendUpdate();
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains(TAG_VERSION, Tag.TAG_INT)) {
            return;
        }
        int version = tag.getInt(TAG_VERSION);
        if (version != SCHEMA_VERSION) {
            Technomancy.LOGGER.warn("Discarding essentia dynamo data with unknown schema {} at {}",
                    version, worldPosition);
            return;
        }
        energy.load(tag.getCompound(TAG_ENERGY));
        if (store.load(tag.getCompound(TAG_ESSENTIA))) {
            Technomancy.LOGGER.warn("Essentia dynamo at {} could not restore its contents verbatim",
                    worldPosition);
        }
        fuel.load(tag.getLong(TAG_FUEL));
        boost = tag.getBoolean(TAG_BOOST);
        redstone.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.put(TAG_ENERGY, energy.save());
        tag.put(TAG_ESSENTIA, store.save());
        tag.putLong(TAG_FUEL, fuel.banked());
        tag.putBoolean(TAG_BOOST, boost);
        redstone.save(tag);
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
