package theflogat.technomancy.common.tiles.nodes;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import dev.tc4port.thaumcraft.api.node.NodeStateChangeResult;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.nodes.NodeFabricatorBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaPorts;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.nodes.NodeFabricatorWork;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;

/**
 * The node fabricator: two of them facing each other six blocks apart work on the aura node
 * suspended between them, putting drawn essentia back into it as Vis.
 *
 * <p>Ported from {@code TileNodeGenerator}. The structure, the distances, the 256-unit single
 * aspect buffer, the 50,000,000 buffer, the suction of 48 over a minimum of 32 and both operation
 * prices are the 1.7.10 numbers. What is deliberately <em>not</em> here is node creation: it
 * needed {@code ThaumcraftWorldGenerator.createNodeAt}, and TC4R's {@code NodeApi} exposes only
 * compare-and-set on nodes that already exist (engineering guide 9.3, matrix row S2 to S4). The
 * fabricator therefore recharges and expands existing nodes and jarred nodes, and does nothing at
 * all when there is no node between the pair.</p>
 *
 * <p>Both operations commit through {@link NodeApi#replaceLoadedState} against a state read in
 * the same tick, and essentia and energy are only debited once the node has actually changed. The
 * original called {@code node.addToContainer} and then {@code takeFromContainer} as two
 * unconnected steps, with no check that the first had done anything.</p>
 */
public final class NodeFabricatorBlockEntity extends BlockEntity implements EssentiaTransport, AspectContainerView {

    /** {@code super(50000000, RedstoneSet.LOW)}. */
    public static final long ENERGY_CAPACITY = 50_000_000;
    /** {@code maxAmount}. */
    public static final int ESSENTIA_CAPACITY = 256;
    /** {@code getSuctionAmount}. */
    public static final int SUCTION = 48;
    /** {@code getMinimumSuction}. */
    public static final int MIN_SUCTION = 32;
    /** Blocks between the two controllers, along the facing. */
    public static final int PARTNER_DISTANCE = 6;
    /** Where the node sits: three blocks along the facing, one up. */
    public static final int NODE_DISTANCE = 3;
    /** {@code RedstoneSet.LOW}: a signal switches the fabricator off. */
    public static final RedstoneMode DEFAULT_REDSTONE_MODE = RedstoneMode.LOW;

    private static final int PULL_INTERVAL = 5;
    private static final int STRUCTURE_INTERVAL = 20;
    private static final String TAG_VERSION = "v";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ESSENTIA = "Essentia";
    private static final String TAG_BOOST = "Boost";
    private static final int SCHEMA_VERSION = 1;

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(ESSENTIA_CAPACITY));
    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE_MODE);
    private boolean boost;
    private int ticks;
    /** Whether the pair is formed; display state, recomputed on the server. */
    private boolean active;
    private boolean syncedActive;
    private int syncedAmount = -1;
    @Nullable
    private AspectId syncedAspect;

    public NodeFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.NODE_FABRICATOR.get(), pos, state);
        // A consumer on every face but the one it looks through, which is where the node is.
        energy = new MachineEnergy(this, EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0),
                EnergyPorts.consumer(EnergyPorts.allExcept(facing())));
        store.setListener(this::setChanged);
        redstone.setListener(this::changedAndSync);
    }

    // ---- geometry ----

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(NodeFabricatorBlock.FACING) ? state.getValue(NodeFabricatorBlock.FACING) : Direction.NORTH;
    }

    /** The eight shell positions of one fabricator: the 3x3 slab across its facing, minus itself. */
    public static List<BlockPos> shellPositions(BlockPos controller, Direction facing) {
        Direction across = facing.getClockWise();
        List<BlockPos> positions = new ArrayList<>(8);
        for (int height = 0; height < 3; height++) {
            for (int width = -1; width < 2; width++) {
                if (height == 0 && width == 0) {
                    continue;
                }
                positions.add(controller.relative(across, width).above(height));
            }
        }
        return positions;
    }

    /** All nine positions of this fabricator, the controller first. */
    public List<BlockPos> structurePositions() {
        List<BlockPos> all = new ArrayList<>(9);
        all.add(worldPosition);
        all.addAll(shellPositions(worldPosition, facing()));
        return all;
    }

    public BlockPos partnerPosition() {
        return worldPosition.relative(facing(), PARTNER_DISTANCE);
    }

    public BlockPos nodePosition() {
        return worldPosition.relative(facing(), NODE_DISTANCE).above();
    }

    // ---- lifecycle ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, NodeFabricatorBlockEntity machine) {
        machine.ticks++;
        if (machine.ticks % STRUCTURE_INTERVAL == 0) {
            machine.formShells(level);
        }
        boolean formed = machine.structureComplete(level);
        machine.setActive(formed && machine.partnerReady(level));
        if (machine.active && machine.canRun(level)) {
            if (level instanceof ServerLevel server) {
                machine.work(server);
            }
            if (machine.ticks % PULL_INTERVAL == 0) {
                machine.pullEssentia(level);
            }
        }
        machine.syncIfChanged();
    }

    /**
     * Puts a shell into every free position of the slab.
     *
     * <p>Deliberate change: {@code createDummyBlocks} called {@code WorldHelper.destroyAndDrop}
     * on all eight positions, so building a fabricator broke whatever stood there - and if one of
     * them could not be broken it destroyed the fabricator itself. Here anything that is not air
     * or replaceable simply leaves the structure unformed, which the block state shows.</p>
     */
    public void formShells(Level level) {
        Direction facing = facing();
        for (BlockPos shellPos : shellPositions(worldPosition, facing)) {
            if (!level.isLoaded(shellPos)) {
                return;
            }
            BlockState existing = level.getBlockState(shellPos);
            if (level.getBlockEntity(shellPos) instanceof NodeFabricatorShellBlockEntity shell) {
                shell.setHost(worldPosition);
                continue;
            }
            if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(shellPos, theflogat.technomancy.common.registry.TechnomBlocks.NODE_FABRICATOR_SHELL
                        .get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(shellPos) instanceof NodeFabricatorShellBlockEntity shell) {
                    shell.setHost(worldPosition);
                }
            }
        }
    }

    /** Takes the shells down again; called when the controller is broken or turned. */
    public void removeShells(Level level) {
        for (BlockPos shellPos : shellPositions(worldPosition, facing())) {
            if (level.isLoaded(shellPos)
                    && level.getBlockEntity(shellPos) instanceof NodeFabricatorShellBlockEntity shell
                    && worldPosition.equals(shell.host())) {
                level.removeBlock(shellPos, false);
            }
        }
    }

    public boolean structureComplete(Level level) {
        for (BlockPos shellPos : shellPositions(worldPosition, facing())) {
            if (!level.isLoaded(shellPos)
                    || !(level.getBlockEntity(shellPos) instanceof NodeFabricatorShellBlockEntity shell)
                    || !worldPosition.equals(shell.host())) {
                return false;
            }
        }
        return true;
    }

    /** A partner fabricator six blocks away, looking back, with its own slab formed. */
    private boolean partnerReady(Level level) {
        BlockPos partnerPos = partnerPosition();
        if (!level.isLoaded(partnerPos)
                || !(level.getBlockEntity(partnerPos) instanceof NodeFabricatorBlockEntity partner)) {
            return false;
        }
        return partner.facing() == facing().getOpposite() && partner.structureComplete(level);
    }

    /**
     * Redstone is read at all nine positions, as {@code canRun()} did.
     *
     * <p>In the default {@link RedstoneMode#LOW} that means a signal anywhere on the slab stops
     * the machine, which is what makes a redstone torch beside a shell a working off switch.</p>
     */
    public boolean canRun(Level level) {
        boolean powered = false;
        for (BlockPos pos : structurePositions()) {
            if (level.isLoaded(pos) && level.hasNeighborSignal(pos)) {
                powered = true;
                break;
            }
        }
        return redstone.mode().canRun(powered);
    }

    // ---- work ----

    /** The node this pair is working on, or {@code null}. */
    @Nullable
    public AuraNodeView node(Level level) {
        BlockPos nodePos = nodePosition();
        return level.isLoaded(nodePos) && level.getBlockEntity(nodePos) instanceof AuraNodeView view ? view : null;
    }

    /**
     * One operation: recharge an aspect, or with a potency gem raise its base.
     *
     * <p>The node state is read fresh, the replacement is computed from it, and the essentia and
     * the energy are taken only when TC4R reports the node actually replaced. A lost race, an
     * unloaded chunk or an energized node all mean "nothing happened this tick".</p>
     *
     * @return the operation that was committed
     */
    public NodeFabricatorWork.Operation work(ServerLevel level) {
        AuraNodeView node = node(level);
        AspectId aspect = store.dominantAspect();
        if (node == null || aspect == null) {
            return NodeFabricatorWork.Operation.NONE;
        }
        AuraNodeState before = node.nodeState();
        int current = before.currentVis().amount(aspect);
        int base = before.baseVis().amount(aspect);
        boolean listed = before.currentVis().amounts().containsKey(aspect);
        NodeFabricatorWork.Plan plan = NodeFabricatorWork.plan(boost, energy.ledger().stored(),
                store.amount(aspect), listed, current, base);
        if (!plan.works()) {
            return NodeFabricatorWork.Operation.NONE;
        }
        LinkedHashMap<AspectId, Integer> currentVis = new LinkedHashMap<>(before.currentVis().amounts());
        currentVis.merge(aspect, 1, Integer::sum);
        AuraNodeState after;
        if (plan.operation() == NodeFabricatorWork.Operation.EXPAND) {
            LinkedHashMap<AspectId, Integer> baseVis = new LinkedHashMap<>(before.baseVis().amounts());
            baseVis.merge(aspect, 1, Integer::sum);
            after = before.withVis(new NodeVis(baseVis), new NodeVis(currentVis));
        } else {
            after = before.withCurrentVis(new NodeVis(currentVis));
        }
        NodeStateChangeResult result =
                NodeApi.replaceLoadedState(level, nodePosition(), before, after, VisAction.EXECUTE);
        if (result != NodeStateChangeResult.REPLACED) {
            return NodeFabricatorWork.Operation.NONE;
        }
        if (!store.takeExact(aspect, plan.essentia(), false) || !energy.ledger().tryConsume(plan.energy())) {
            // Both were checked against the same snapshot a few lines up, so this cannot happen
            // without a concurrent modification; the node keeps the Vis and the loss is logged
            // rather than papered over by writing the node back.
            Technomancy.LOGGER.error("Node fabricator at {} gave away {} Vis it could not pay for",
                    worldPosition, plan.operation());
        }
        setChanged();
        return plan.operation();
    }

    // ---- essentia intake ----

    /**
     * Takes one unit of essentia for the whole machine, trying the faces of the controller and of
     * every shell in turn.
     *
     * <p>The original pulled once per face per tick from the controller <em>and</em> from all
     * eight shells, so a fabricator next to a bank of jars filled at up to forty units a tick.
     * One unit per {@value #PULL_INTERVAL} ticks for the whole structure is the tube rate the
     * rest of this port uses.</p>
     */
    private void pullEssentia(Level level) {
        if (store.total() >= ESSENTIA_CAPACITY) {
            return;
        }
        AspectId held = store.dominantAspect();
        Direction facing = facing();
        for (BlockPos pos : structurePositions()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (face == facing) {
                    continue;
                }
                BlockPos neighbourPos = pos.relative(face);
                BlockEntity neighbourEntity = level.getBlockEntity(neighbourPos);
                if (neighbourEntity instanceof NodeFabricatorBlockEntity
                        || neighbourEntity instanceof NodeFabricatorShellBlockEntity) {
                    continue;
                }
                EssentiaTransport neighbour = ThaumcraftApiHelper.getConnectableTransport(level, pos, face);
                if (neighbour == null) {
                    continue;
                }
                Direction theirFace = face.getOpposite();
                if (!neighbour.canOutputTo(theirFace)) {
                    continue;
                }
                if (!EssentiaSuction.canTake(SUCTION, neighbour.suctionAmount(theirFace))
                        || SUCTION < neighbour.minimumSuction()) {
                    continue;
                }
                AspectId selected = held != null ? held : neighbour.essentiaType(theirFace);
                if (selected == null || !known(selected) || neighbour.essentiaAmount(theirFace) <= 0) {
                    continue;
                }
                int taken = EssentiaApi.take(level, neighbour, selected, 1, theirFace, EssentiaTransferMode.EXECUTE);
                if (taken <= 0) {
                    continue;
                }
                int accepted = store.add(selected, taken, false);
                if (accepted < taken) {
                    Technomancy.LOGGER.warn("Node fabricator at {} lost {} {} it had already pulled",
                            worldPosition, taken - accepted, selected);
                }
                return;
            }
        }
    }

    private static boolean known(@Nullable AspectId aspect) {
        return aspect != null && AspectApi.registry().get(aspect).isPresent();
    }

    // ---- accessors ----

    public EssentiaStore store() {
        return store;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    public boolean isBoosted() {
        return boost;
    }

    public boolean setBoosted(boolean installed) {
        if (boost == installed) {
            return false;
        }
        boost = installed;
        changedAndSync();
        return true;
    }

    /** Whether the pair is formed and facing each other. */
    public boolean isActive() {
        return active;
    }

    private void setActive(boolean value) {
        if (active != value) {
            active = value;
            setChanged();
        }
    }

    /** Rebuilds the port rules and the shells after the facing changed. */
    public void refreshStructure(Level level) {
        energy.setPorts(EnergyPorts.consumer(EnergyPorts.allExcept(facing())));
        formShells(level);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            energy.setPorts(EnergyPorts.consumer(EnergyPorts.allExcept(facing())));
        }
    }

    // ---- essentia transport ----

    private EssentiaPorts ports() {
        Direction[] faces = new Direction[Direction.values().length - 1];
        int index = 0;
        for (Direction face : Direction.values()) {
            if (face != facing()) {
                faces[index++] = face;
            }
        }
        return EssentiaPorts.both(faces);
    }

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
        return ports().isConnectable(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return ports().canInputFrom(face);
    }

    /** Essentia can be taken back out, which is how a labelled jar clears a wrong aspect. */
    @Override
    public boolean canOutputTo(Direction face) {
        return ports().canOutputTo(face);
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        AspectId held = store.dominantAspect();
        return known(held) ? held : null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return canInputFrom(face) && store.total() < ESSENTIA_CAPACITY ? SUCTION : 0;
    }

    @Override
    public int minimumSuction() {
        return MIN_SUCTION;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return canOutputTo(face) ? store.take(aspect, amount, !mode.executes()) : 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canInputFrom(face) || !known(aspect)) {
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

    @Override
    public int essentiaAmount(Direction face) {
        return store.total();
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return canOutputTo(face) ? essentiaType(face) : null;
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return canOutputTo(face) ? store.amount(aspect) : 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
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

    // ---- sync and persistence ----

    private void syncIfChanged() {
        AspectId aspect = store.dominantAspect();
        int amount = store.total();
        if (amount == syncedAmount && active == syncedActive && java.util.Objects.equals(aspect, syncedAspect)) {
            return;
        }
        syncedAmount = amount;
        syncedActive = active;
        syncedAspect = aspect;
        sendUpdate();
    }

    private void sendUpdate() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void changedAndSync() {
        setChanged();
        sendUpdate();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains(TAG_VERSION, Tag.TAG_INT)) {
            return;
        }
        if (tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
            Technomancy.LOGGER.warn("Discarding node fabricator data with unknown schema {} at {}",
                    tag.getInt(TAG_VERSION), worldPosition);
            return;
        }
        energy.load(tag.getCompound(TAG_ENERGY));
        if (store.load(tag.getCompound(TAG_ESSENTIA))) {
            Technomancy.LOGGER.warn("Node fabricator at {} could not restore its contents verbatim", worldPosition);
        }
        boost = tag.getBoolean(TAG_BOOST);
        redstone.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.put(TAG_ENERGY, energy.save());
        tag.put(TAG_ESSENTIA, store.save());
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
