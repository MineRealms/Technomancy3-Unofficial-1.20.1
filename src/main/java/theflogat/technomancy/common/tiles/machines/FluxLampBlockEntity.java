package theflogat.technomancy.common.tiles.machines;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.block.entity.InfusionMatrixBlockEntity;
import dev.tc4port.thaumcraft.registry.TCFluids;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.compat.thaumcraft.ThaumcraftInternals;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileFluxLamp}: spends ordo to calm a running infusion altar and drains the instability
 * off as flux goo.
 *
 * <p>Every number is the original's — 32 units of ordo held, suction 128, five ordo per point of
 * instability, at most five points per working cycle, one cycle every ten ticks, 200 mB of flux
 * goo per point, a 1,000 mB tank — and the altar search is the original's 21x10x21 box around
 * the lamp. The box is only walked when the remembered altar has gone, so a lamp next to a
 * working altar costs a single {@code getBlockEntity} a cycle rather than 4,410.</p>
 *
 * <p>The lamp is a load, not a source: it takes ordo in and gives flux goo out, so the pipe that
 * feeds it and the pipe that drains it are different pipes.</p>
 */
public final class FluxLampBlockEntity extends BlockEntity implements EssentiaTransport {

    /** {@code maxAmount}. */
    public static final int ORDO_CAPACITY = 32;
    /** {@code takeFromContainer(ORDER, 5)}: ordo per point of instability removed. */
    public static final int ORDO_PER_POINT = 5;
    /** The stabilise loop runs at most this many times per cycle. */
    public static final int MAX_POINTS_PER_CYCLE = 5;
    /** {@code ++count % 10}. */
    public static final int CYCLE_TICKS = 10;
    /** Flux goo made per point of instability removed. */
    public static final int GOO_PER_POINT = 200;
    /** {@code new FluidTank(1000)}. */
    public static final int TANK_CAPACITY = 1_000;

    private static final AspectId ORDO = AspectId.parse("ordo");
    private static final EssentiaSuction SUCTION = new EssentiaSuction(128, 128);
    /** Half-open search box of the original: {@code x,z in [-10, 9]}, {@code y in [-5, 4]}. */
    private static final int SEARCH_NEGATIVE = 10;
    private static final int SEARCH_POSITIVE = 9;
    private static final int SEARCH_DOWN = 5;
    private static final int SEARCH_UP = 4;

    private static final String TAG_ESSENTIA = "Essentia";
    private static final String TAG_TANK = "Tank";
    private static final String TAG_MATRIX = "Matrix";
    private static final String TAG_COUNT = "count";
    private static final String TAG_STABILISE = "stabilise";

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(ORDO_CAPACITY));
    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(TCFluids.FLUX_GOO.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> fluidView = LazyOptional.of(() -> tank);

    @Nullable
    private BlockPos matrix;
    private int count;
    private boolean stabilise;

    public FluxLampBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.FLUX_LAMP.get(), pos, state);
        store.setListener(this::setChanged);
        store.addFilter(ORDO);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxLampBlockEntity lamp) {
        if (++lamp.count < CYCLE_TICKS) {
            return;
        }
        lamp.count = 0;
        // The original only fed itself on the working cycle, and only while it had room.
        if (!lamp.full()) {
            lamp.fill(level);
        }
        InfusionMatrixBlockEntity altar = lamp.altar(level);
        if (altar == null) {
            return;
        }
        // The original only re-armed while the altar was idle, so a lamp cannot be used to
        // brute-force an infusion that is already running.
        if (!altar.crafting()) {
            lamp.stabilise = true;
        }
        if (!lamp.stabilise || altar.instability() <= 0) {
            return;
        }
        for (int point = 0; point < MAX_POINTS_PER_CYCLE && altar.instability() > 0; point++) {
            // Order matters: the instability has to come down before the ordo is spent, or a
            // TC4R build whose instability field cannot be reached would destroy the ordo for
            // nothing. Upstream took first because its own field could not fail.
            if (lamp.tank.getSpace() < GOO_PER_POINT
                    || lamp.store.amount(ORDO) < ORDO_PER_POINT
                    || !ThaumcraftInternals.reduceInstability(altar, 1)
                    || !lamp.store.takeExact(ORDO, ORDO_PER_POINT, false)) {
                break;
            }
            lamp.tank.fill(new FluidStack(TCFluids.FLUX_GOO.get(), GOO_PER_POINT),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        lamp.stabilise = false;
        lamp.setChanged();
    }

    @Nullable
    private InfusionMatrixBlockEntity altar(Level level) {
        if (matrix != null && level.getBlockEntity(matrix) instanceof InfusionMatrixBlockEntity found) {
            return found;
        }
        matrix = null;
        for (int dx = -SEARCH_NEGATIVE; dx <= SEARCH_POSITIVE; dx++) {
            for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
                for (int dz = -SEARCH_NEGATIVE; dz <= SEARCH_POSITIVE; dz++) {
                    BlockPos at = getBlockPos().offset(dx, dy, dz);
                    if (level.getBlockEntity(at) instanceof InfusionMatrixBlockEntity found) {
                        matrix = at;
                        return found;
                    }
                }
            }
        }
        return null;
    }

    public EssentiaStore store() {
        return store;
    }

    public FluidTank tank() {
        return tank;
    }

    // ---- essentia ----

    @Override
    public boolean isConnectable(Direction face) {
        return face == Direction.UP;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return face == Direction.UP;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Override
    @Nullable
    public AspectId suctionType(Direction face) {
        return full() ? null : ORDO;
    }

    @Override
    public int suctionAmount(Direction face) {
        return full() ? 0 : SUCTION.amount(true, false, false, 0);
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        int taken = store.add(aspect, amount, mode == EssentiaTransferMode.SIMULATE);
        if (taken > 0 && mode == EssentiaTransferMode.EXECUTE) {
            setChanged();
        }
        return taken;
    }

    /**
     * Deliberately answers "nothing to give", even though the original returned what it held.
     * This is defect A-7: a TC4R buffer tube asks how much a neighbour has, takes it, and then
     * gives up on that face for the tick - so a block that reports contents it will never hand
     * over steals every face the tube visits. The project fixed the same shape on the processor
     * and the condenser; the lamp is input-only for the same reason.
     */
    @Override
    @Nullable
    public AspectId essentiaType(Direction face) {
        return null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int minimumSuction() {
        return SUCTION.minimum(true, store.amount(ORDO));
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    private boolean full() {
        return store.total() >= store.limits().totalCapacity();
    }

    /**
     * Pulls one unit of ordo from whatever is above, the way the original's {@code fill()} did:
     * only if that neighbour can give downward, holds ordo, advertises less suction than the
     * lamp's 128, and does not demand more than 128 before it lets go.
     */
    private void fill(Level level) {
        EssentiaTransport neighbour =
                ThaumcraftApiHelper.getConnectableTransport(level, getBlockPos(), Direction.UP);
        Direction theirs = Direction.DOWN;
        if (neighbour == null || !neighbour.canOutputTo(theirs) || neighbour.essentiaAmount(theirs) <= 0
                || !ORDO.equals(neighbour.essentiaType(theirs))
                || neighbour.suctionAmount(theirs) >= suctionAmount(Direction.UP)
                || suctionAmount(Direction.UP) < neighbour.minimumSuction()) {
            return;
        }
        if (store.add(ORDO, 1, true) <= 0) {
            return;
        }
        int taken = neighbour.takeEssentia(ORDO, 1, theirs, EssentiaTransferMode.EXECUTE);
        if (taken > 0 && store.add(ORDO, taken, false) < taken) {
            Technomancy.LOGGER.error("The flux lamp at {} lost {} ordo it had already pulled",
                    getBlockPos(), taken);
        }
    }

    // ---- capabilities and storage ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidView.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidView.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ESSENTIA, store.save());
        tag.put(TAG_TANK, tank.writeToNBT(new CompoundTag()));
        tag.putInt(TAG_COUNT, count);
        tag.putBoolean(TAG_STABILISE, stabilise);
        if (matrix != null) {
            tag.putIntArray(TAG_MATRIX, new int[] {matrix.getX(), matrix.getY(), matrix.getZ()});
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        store.load(tag.getCompound(TAG_ESSENTIA));
        tank.readFromNBT(tag.getCompound(TAG_TANK));
        count = tag.getInt(TAG_COUNT);
        stabilise = tag.getBoolean(TAG_STABILISE);
        if (tag.contains(TAG_MATRIX)) {
            int[] at = tag.getIntArray(TAG_MATRIX);
            if (at.length == 3) {
                matrix = new BlockPos(at[0], at[1], at[2]);
            }
        }
    }
}
