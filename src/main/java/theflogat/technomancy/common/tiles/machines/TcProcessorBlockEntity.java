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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaPorts;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.common.machines.processing.OreProcessing;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The Ignis Incinerator ({@code TileTCProcessor}): purifies ores and pays for it in ignis.
 *
 * <p>It takes no energy at all, which is the original's design and is kept deliberately — the
 * Blood Magic and Botania processors pay with their own resources, and the point of the three is
 * that each ore can be passed through every system once or twice for a further stage.</p>
 *
 * <p>Essentia only ever goes in. The original advertised {@code isConnectable} and
 * {@code canInputFrom} as {@code true} on every face and {@code canOutputTo} as {@code false},
 * but still answered {@code getEssentiaType}/{@code getEssentiaAmount} with its buffer contents;
 * a TC4R buffer tube checks the amount before the direction and gives up without scanning its
 * other faces, so those answers are zero here (the same rule the dynamo needed, defect A-7).</p>
 */
public final class TcProcessorBlockEntity extends ProcessorBlockEntity
        implements EssentiaTransport, AspectContainerView {

    /** {@code TileTCProcessor.maxAmount}. */
    public static final int CAPACITY = 64;
    /** {@code getSuctionAmount}: 128 while there is room, else 0. */
    public static final int SUCTION = 128;
    /** {@code getMinimumSuction}. It never gives essentia away, so this only gates other pulls. */
    public static final int MINIMUM_SUCTION = 128;

    public static final AspectId IGNIS = AspectId.parse("ignis");

    private static final EssentiaPorts PORTS = EssentiaPorts.consumer(Direction.values());
    private static final String TAG_ESSENTIA = "Essentia";

    private final EssentiaStore store = new EssentiaStore(EssentiaLimits.jar(CAPACITY));

    public TcProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.PROCESSOR_TC.get(), pos, state);
        // A fixed filter, so the store refuses anything but ignis and advertises ignis suction
        // even while empty.
        store.setFilters(List.of(IGNIS));
        store.setListener(this::setChanged);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TcProcessorBlockEntity processor) {
        processor.pullFromNeighbours(level);
        processor.processTick(level, pos, state);
    }

    @Override
    public ProcessingModule module() {
        return ProcessingModule.THAUMCRAFT;
    }

    @Override
    protected boolean payTick(OreProcessing.Job job) {
        // All-or-nothing: a partial payment would advance the cycle without covering it.
        return store.takeExact(IGNIS, job.tickCost(), false);
    }

    @Override
    public int fuelAmount() {
        return store.amount(IGNIS);
    }

    @Override
    public int fuelCapacity() {
        return CAPACITY;
    }

    public EssentiaStore store() {
        return store;
    }

    /** One unit per face per tick from any neighbour it out-sucks, as {@code perform()} did. */
    private void pullFromNeighbours(Level level) {
        if (!AspectApi.contains(IGNIS)) {
            return;
        }
        for (Direction face : Direction.values()) {
            if (store.space(IGNIS) <= 0) {
                return;
            }
            EssentiaTransport neighbour = ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, face);
            Direction theirs = face.getOpposite();
            if (neighbour == null || !neighbour.canOutputTo(theirs)
                    || neighbour.availableEssentia(IGNIS, theirs) <= 0) {
                continue;
            }
            if (!EssentiaSuction.canDiscover(suctionAmount(face), neighbour.suctionAmount(theirs),
                    neighbour.minimumSuction())) {
                continue;
            }
            if (store.add(IGNIS, 1, true) <= 0) {
                continue;
            }
            int taken = EssentiaApi.take(level, neighbour, IGNIS, 1, theirs, EssentiaTransferMode.EXECUTE);
            if (taken > 0 && store.add(IGNIS, taken, false) < taken) {
                Technomancy.LOGGER.error("Processor at {} lost ignis it had already pulled", worldPosition);
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
        return false;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return PORTS.canInputFrom(face) && AspectApi.contains(IGNIS) ? IGNIS : null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return suctionType(face) != null && store.space(IGNIS) > 0 ? SUCTION : 0;
    }

    @Override
    public int minimumSuction() {
        return MINIMUM_SUCTION;
    }

    /** Fuel never comes back out. */
    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!PORTS.canInputFrom(face) || !IGNIS.equals(aspect)) {
            return 0;
        }
        return store.add(aspect, amount, !mode.executes());
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (store.load(tag.getCompound(TAG_ESSENTIA))) {
            Technomancy.LOGGER.warn("Processor at {} could not restore its fuel verbatim", worldPosition);
        }
        // load() clears the filters, and a filterless store would accept any aspect a pipe offers.
        store.setFilters(List.of(IGNIS));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ESSENTIA, store.save());
    }
}
