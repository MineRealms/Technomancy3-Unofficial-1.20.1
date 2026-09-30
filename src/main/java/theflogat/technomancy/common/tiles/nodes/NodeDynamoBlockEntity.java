package theflogat.technomancy.common.tiles.nodes;

import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import dev.tc4port.thaumcraft.api.node.NodeStateChangeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.joml.Vector3f;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.nodes.NodeDynamoBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.nodes.NodeVisDrain;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.dynamo.DynamoFuelBank;
import theflogat.technomancy.compat.gtceu.EuTier;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The node dynamo: takes whole Vis from aura nodes and node jars within four blocks and burns it
 * into Forge Energy (and GT EU where a neighbour speaks it) through its single output face.
 *
 * <p>Ported from {@code TileNodeDynamo} + {@code TileDynamoBase}. Rates, buffer and output are
 * the essentia dynamo's (both extended the same base). A Vis is worth
 * {@value #VIS_FUEL_POINTS} fuel points ({@code extractFuel} returned 30 ticks of 80 RF), scaled
 * by {@code balance.essentiaFuelScale} exactly like essentia, so node Vis keeps its 1.7.10 value
 * relative to every aspect (30 : 800 against ignis) whatever the server's scale. Sustained
 * output is bounded by the nodes' own recharge (one Vis per 400-900 ticks per node in TC4R's
 * default ecology), i.e. 1-1.5 Q/t per node at the default scale.</p>
 *
 * <p>Every Vis leaves a node through {@link NodeApi#replaceLoadedState}, a compare-and-set on a
 * freshly read state, so a concurrent recharge, wand or second dynamo can never be overwritten
 * and the Vis is only credited once the node has actually changed.</p>
 */
public final class NodeDynamoBlockEntity extends BlockEntity implements EnergyHolder {

    public static final long ENERGY_CAPACITY = 40_000;
    public static final long MAX_OUTPUT = 320;
    public static final long BASE_RATE = 80;
    public static final long BOOSTED_RATE = 320;
    public static final int BOOSTED_UNITS = (int) (BOOSTED_RATE / BASE_RATE);
    /** {@code extractFuel} returned 30 ticks of fuel per Vis. */
    public static final int VIS_FUEL_POINTS = 30;
    /**
     * Whole Vis held between drains. {@code maxAmount} was 32, but the inner test
     * {@code amount < 16} stopped every refill at 16, so 16 is what the original actually held.
     */
    public static final int VIS_CAPACITY = 16;
    /** {@code getNodes()} scanned x/y/z from -4 to 4. */
    public static final int RANGE = 4;
    /** {@code counter++ == 20} followed by {@code counter = 0}: one attempt every 21 ticks. */
    public static final int DRAIN_INTERVAL = 21;
    /** Same default as the essentia dynamo port: works without a signal. */
    public static final RedstoneMode DEFAULT_REDSTONE_MODE = RedstoneMode.NONE;

    private static final int FUEL_LOOKAHEAD_TICKS = 32;
    private static final String TAG_VERSION = "v";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_VIS = "Vis";
    private static final String TAG_FUEL = "Fuel";
    private static final String TAG_BOOST = "Boost";
    private static final int SCHEMA_VERSION = 1;

    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE_MODE);
    private final boolean euCapable;
    private final DynamoFuelBank fuel = new DynamoFuelBank(FUEL_LOOKAHEAD_TICKS);
    private int vis;
    private boolean boost;
    private int ticks;

    public NodeDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.NODE_DYNAMO.get(), pos, state);
        EnergyLimits limits = energyLimits();
        euCapable = limits.emitsEu();
        energy = new MachineEnergy(this, limits, energyPorts(facing(), euCapable));
        redstone.setListener(this::changedAndSync);
    }

    /** Identical shape to the essentia dynamo: never accepts, emits up to 320 Q/t. */
    private static EnergyLimits energyLimits() {
        EnergyLimits limits = EnergyLimits.fe(ENERGY_CAPACITY, 0, MAX_OUTPUT);
        long voltage = EuTier.LV.voltage();
        long amps = MAX_OUTPUT / (voltage * EnergyUnits.qPerEu());
        return amps > 0 ? limits.withEuOutput(voltage, amps) : limits;
    }

    private static EnergyPorts energyPorts(Direction facing, boolean eu) {
        int mask = EnergyPorts.mask(facing);
        return eu ? EnergyPorts.generator(mask) : new EnergyPorts(0, mask, 0, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NodeDynamoBlockEntity dynamo) {
        dynamo.ticks++;
        boolean running = dynamo.redstone.canRun(level, pos);
        if (running) {
            dynamo.burn();
            // Inside the redstone gate. The original drained nodes on its own counter outside
            // set.canRun, so a dynamo switched off by redstone still emptied every node in range
            // into a buffer it would never burn (same shape as essentia dynamo defect A-18).
            if (dynamo.ticks % DRAIN_INTERVAL == 0 && dynamo.vis < VIS_CAPACITY
                    && level instanceof ServerLevel server) {
                dynamo.drainOneVis(server);
            }
        }
        dynamo.energy.pushOutput();
        dynamo.updateWorkingState(running);
    }

    // ---- generation ----

    public long ratePerTick() {
        return boost ? BOOSTED_RATE : BASE_RATE;
    }

    public int unitsPerCharge() {
        return boost ? BOOSTED_UNITS : 1;
    }

    /** Q one whole Vis is worth at the current fuel scale. */
    public static long energyPerVis() {
        return EssentiaFuelTable.energyPerUnit(VIS_FUEL_POINTS, TechnomancyConfig.ESSENTIA_FUEL_SCALE.get());
    }

    private void burn() {
        DynamoFuelBank.Burn burn = fuel.tick(ratePerTick(), unitsPerCharge(), vis,
                NodeDynamoBlockEntity::energyPerVis, energy.ledger().space());
        if (burn.unitsConsumed() > 0) {
            vis -= burn.unitsConsumed();
            setChanged();
        }
        if (burn.energyProduced() > 0) {
            long stored = energy.ledger().generate(burn.energyProduced());
            fuel.refund(burn.energyProduced() - stored);
        }
    }

    // ---- node drain ----

    /**
     * Takes one whole Vis from one node in range, trying nodes in random order.
     *
     * <p>Only chunks that are already loaded are looked at, and only their block entity maps,
     * never all 729 positions: the original called {@code getTileEntity} on every one of them
     * each second, loading chunks at the edge of the cube as it went.</p>
     *
     * @return whether a Vis was taken
     */
    boolean drainOneVis(ServerLevel level) {
        List<BlockPos> nodes = nodesInRange(level);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            // Fisher-Yates from the back: a random order without a second list.
            int j = level.random.nextInt(i + 1);
            BlockPos nodePos = nodes.get(j);
            nodes.set(j, nodes.get(i));
            if (!(level.getBlockEntity(nodePos) instanceof AuraNodeView node)) {
                continue;
            }
            AuraNodeState before = node.nodeState();
            Optional<NodeVisDrain.Drain> drain = NodeVisDrain.pick(before.currentVis(), level.random);
            if (drain.isEmpty()) {
                continue;
            }
            AuraNodeState after = before.withCurrentVis(drain.get().after());
            NodeStateChangeResult result =
                    NodeApi.replaceLoadedState(level, nodePos, before, after, VisAction.EXECUTE);
            if (result != NodeStateChangeResult.REPLACED) {
                // Energized nodes and third-party views are not mutable owners; anything else is a
                // lost race, which simply means "try again next time".
                continue;
            }
            vis++;
            setChanged();
            showDrain(level, nodePos, drain.get().aspect());
            return true;
        }
        return false;
    }

    private List<BlockPos> nodesInRange(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos origin = worldPosition;
        int minX = SectionPos.blockToSectionCoord(origin.getX() - RANGE);
        int maxX = SectionPos.blockToSectionCoord(origin.getX() + RANGE);
        int minZ = SectionPos.blockToSectionCoord(origin.getZ() - RANGE);
        int maxZ = SectionPos.blockToSectionCoord(origin.getZ() + RANGE);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity candidate : chunk.getBlockEntities().values()) {
                    BlockPos p = candidate.getBlockPos();
                    if (candidate instanceof AuraNodeView
                            && Math.abs(p.getX() - origin.getX()) <= RANGE
                            && Math.abs(p.getY() - origin.getY()) <= RANGE
                            && Math.abs(p.getZ() - origin.getZ()) <= RANGE) {
                        found.add(p.immutable());
                    }
                }
            }
        }
        return found;
    }

    /**
     * A particle trail from node to dynamo in the aspect's colour.
     *
     * <p>Replaces {@code UtilsFX.drawFloatyLine}; the original's beam depended on
     * {@code draining}/{@code sourceX..Z}/{@code color}, none of which were ever synced, so it
     * never appeared outside the integrated server's own tile.</p>
     */
    private void showDrain(ServerLevel level, BlockPos from, dev.tc4port.thaumcraft.api.aspect.AspectId aspect) {
        int rgb = AspectApi.registry().get(aspect).map(d -> d.color()).orElse(0xFFFFFF);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), 0.8F);
        int steps = 8;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            level.sendParticles(dust,
                    Mth.lerp(t, from.getX() + 0.5, worldPosition.getX() + 0.5),
                    Mth.lerp(t, from.getY() + 0.5, worldPosition.getY() + 0.9),
                    Mth.lerp(t, from.getZ() + 0.5, worldPosition.getZ() + 0.5),
                    1, 0, 0, 0, 0);
        }
    }

    // ---- state ----

    public int vis() {
        return vis;
    }

    public long fuel() {
        return fuel.banked();
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

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(NodeDynamoBlock.FACING) ? state.getValue(NodeDynamoBlock.FACING) : Direction.DOWN;
    }

    /** Turns the output to the next face unconditionally (defect A-19 of the shared dynamo base). */
    public Direction cycleFacing() {
        Direction next = Direction.from3DDataValue((facing().get3DDataValue() + 1) % Direction.values().length);
        if (level != null && getBlockState().hasProperty(NodeDynamoBlock.FACING)) {
            level.setBlock(worldPosition, getBlockState().setValue(NodeDynamoBlock.FACING, next), Block.UPDATE_ALL);
        }
        refreshPorts();
        return facing();
    }

    private void refreshPorts() {
        energy.setPorts(energyPorts(facing(), euCapable));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshPorts();
    }

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

    private void updateWorkingState(boolean running) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(NodeDynamoBlock.LIT)) {
            return;
        }
        boolean lit = running && fuel.banked() > 0;
        if (state.getValue(NodeDynamoBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(NodeDynamoBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains(TAG_VERSION, Tag.TAG_INT)) {
            return;
        }
        if (tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
            Technomancy.LOGGER.warn("Discarding node dynamo data with unknown schema {} at {}",
                    tag.getInt(TAG_VERSION), worldPosition);
            return;
        }
        energy.load(tag.getCompound(TAG_ENERGY));
        vis = Mth.clamp(tag.getInt(TAG_VIS), 0, VIS_CAPACITY);
        fuel.load(tag.getLong(TAG_FUEL));
        boost = tag.getBoolean(TAG_BOOST);
        redstone.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.put(TAG_ENERGY, energy.save());
        tag.putInt(TAG_VIS, vis);
        tag.putLong(TAG_FUEL, fuel.banked());
        tag.putBoolean(TAG_BOOST, boost);
        redstone.save(tag);
    }
}
