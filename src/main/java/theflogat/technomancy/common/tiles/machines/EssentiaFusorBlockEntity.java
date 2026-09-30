package theflogat.technomancy.common.tiles.machines;

import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.EssentiaSuction;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.machines.fusor.EssentiaFusorBalance;
import theflogat.technomancy.common.machines.fusor.FusorSides;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The essentia fusor ({@code TileEssentiaFusor}): two aspects in through two marked sides, the
 * compound they combine into out through a third.
 *
 * <p>The recipe is whatever TC4R's own aspect registry says the two components make
 * ({@code AspectApi.combination}), so it follows the aspect data pack rather than a table of our
 * own. One fusion consumes one unit from each input for one unit of output — the 2:1 exchange is
 * why a fusor cannot multiply essentia — and pays {@link EssentiaFusorBalance#costQ} in energy,
 * which is deliberately no longer the original's flat 1000 FE.</p>
 */
public final class EssentiaFusorBlockEntity extends BlockEntity
        implements EssentiaTransport, AspectContainerView, EnergyHolder {

    /** {@code getSuctionAmount}: what a marked input advertises. */
    public static final int INPUT_SUCTION = 48;
    /**
     * {@code getSuctionAmount}/{@code getMinimumSuction}: the output advertises negative suction,
     * which is how a TC4 endpoint says "anything at all may take this from me".
     */
    public static final int OUTPUT_SUCTION = -48;

    /** {@code TileEssentiaFusor()} passed {@code RedstoneSet.HIGH}: it fuses only when powered. */
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.HIGH;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_SIDES = "Fusor";
    private static final String TAG_REDSTONE = "Redstone";

    private final FusorSides sides = new FusorSides();
    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);

    private boolean syncPending;

    public EssentiaFusorBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ESSENTIA_FUSOR.get(), pos, state);
        // Energy in on every face but the top, which is the interaction surface; never out.
        energy = new MachineEnergy(this, EnergyLimits.fe(EssentiaFusorBalance.ENERGY_CAPACITY_Q,
                        EssentiaFusorBalance.ENERGY_CAPACITY_Q, 0),
                EnergyPorts.consumer(EnergyPorts.allExcept(Direction.UP)));
        redstone.setListener(this::changedAndSync);
    }

    /** The registry's own unordered-pair lookup, i.e. the recipe book of this machine. */
    public static FusorSides.Combiner combiner() {
        return (first, second) -> first == null || second == null ? null
                : AspectApi.combination(first, second).orElse(null);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaFusorBlockEntity fusor) {
        // Both halves are gated on a complete recipe, as the original was: an unconfigured fusor
        // neither pulls nor fuses.
        if (fusor.sides.fullyMarked()) {
            fusor.pullInputs(level);
            fusor.tryFuse(level, pos);
        }
        if (fusor.syncPending) {
            fusor.syncPending = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public FusorSides sides() {
        return sides;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    /** Energy the next fusion will cost, for probes and the design record. */
    public long fusionCostQ() {
        return EssentiaFusorBalance.costQ(EssentiaFuelLoader.table(),
                TechnomancyConfig.ESSENTIA_FUEL_SCALE.get(), sides.outputAspect());
    }

    /** One unit into each input per tick from a neighbour it out-sucks, as {@code fill()} did. */
    private void pullInputs(Level level) {
        for (Direction face : sides.inputs()) {
            AspectId wanted = sides.aspect(face);
            if (wanted == null || sides.space(face, wanted) <= 0 || !AspectApi.contains(wanted)) {
                continue;
            }
            EssentiaTransport neighbour = ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, face);
            Direction theirs = face.getOpposite();
            if (neighbour == null || !neighbour.canOutputTo(theirs)
                    || neighbour.availableEssentia(wanted, theirs) <= 0) {
                continue;
            }
            if (!EssentiaSuction.canDiscover(suctionAmount(face), neighbour.suctionAmount(theirs),
                    neighbour.minimumSuction())) {
                continue;
            }
            // Simulate before the irreversible take, never the other way round.
            if (sides.add(face, wanted, 1, true) <= 0) {
                continue;
            }
            int taken = EssentiaApi.take(level, neighbour, wanted, 1, theirs, EssentiaTransferMode.EXECUTE);
            if (taken > 0 && sides.add(face, wanted, taken, false) < taken) {
                Technomancy.LOGGER.error("Fusor at {} lost {} it had already pulled", worldPosition, wanted);
            }
            markChanged();
        }
    }

    /**
     * Runs one fusion if it is powered, affordable and has somewhere to put the result.
     *
     * <p>Every condition is checked before anything is spent, and the energy is taken
     * all-or-nothing, so a blocked or unpowered fusor costs nothing.</p>
     */
    private void tryFuse(Level level, BlockPos pos) {
        if (!redstone.canRun(level, pos) || !sides.canFuse()) {
            return;
        }
        long cost = fusionCostQ();
        if (energy.ledger().stored() < cost || !energy.ledger().tryConsume(cost)) {
            return;
        }
        sides.fuse();
        markChanged();
    }

    // ---- essentia views ----

    @Override
    public AspectAmounts visibleAspects() {
        LinkedHashMap<AspectId, Integer> visible = new LinkedHashMap<>();
        for (Direction face : FusorSides.SLOTS) {
            AspectId aspect = sides.aspect(face);
            if (aspect != null && sides.amount(face) > 0) {
                visible.merge(aspect, sides.amount(face), Integer::sum);
            }
        }
        return visible.isEmpty() ? AspectAmounts.EMPTY : new AspectAmounts(visible);
    }

    /** Marked but empty slots are shown too, which is what makes the configuration inspectable. */
    @Override
    public List<AspectId> visibleAspectOrder() {
        List<AspectId> order = new ArrayList<>(visibleAspects().amounts().keySet());
        for (Direction face : FusorSides.SLOTS) {
            AspectId aspect = sides.aspect(face);
            if (aspect != null && !order.contains(aspect)) {
                order.add(aspect);
            }
        }
        return List.copyOf(order);
    }

    @Override
    public boolean isConnectable(Direction face) {
        return canInputFrom(face) || canOutputTo(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return sides.type(face) == FusorSides.SideType.INPUT && sides.fullyMarked();
    }

    /**
     * The output side always gives; an input side gives only while the machine has no complete
     * recipe, which is how buffered essentia can be recovered after reconfiguring.
     */
    @Override
    public boolean canOutputTo(Direction face) {
        return switch (sides.type(face)) {
            case OUTPUT -> true;
            case INPUT -> !sides.fullyMarked();
            case NONE -> false;
        };
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        AspectId aspect = sides.aspect(face);
        return aspect != null && AspectApi.contains(aspect) ? aspect : null;
    }

    @Override
    public int suctionAmount(Direction face) {
        if (!sides.fullyMarked()) {
            return 0;
        }
        return switch (sides.type(face)) {
            // A full input stops asking; the original kept advertising 48 at 64/64 and had tubes
            // offering it essentia it could not take.
            case INPUT -> suctionType(face) != null && sides.space(face, sides.aspect(face)) > 0
                    ? INPUT_SUCTION : 0;
            case OUTPUT -> OUTPUT_SUCTION;
            case NONE -> 0;
        };
    }

    @Override
    public int minimumSuction() {
        return OUTPUT_SUCTION;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        // The original ignored its own direction rules here and let anything drain an input side
        // even while the machine was running.
        if (!canOutputTo(face)) {
            return 0;
        }
        return sides.take(face, aspect, amount, !mode.executes());
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canInputFrom(face)) {
            return 0;
        }
        int accepted = sides.add(face, aspect, amount, !mode.executes());
        if (accepted > 0 && mode.executes()) {
            markChanged();
        }
        return accepted;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return canOutputTo(face) ? suctionType(face) : null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return essentiaType(face) == null ? 0 : sides.amount(face);
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return essentiaType(face);
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        AspectId held = essentiaType(face);
        return held != null && held.equals(aspect) ? sides.amount(face) : 0;
    }

    /** {@code renderExtendedTube}: the original drew flush tubes on the fusor. */
    @Override
    public boolean renderExtendedTube() {
        return false;
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

    /** Amounts change every tick, so they are saved but only synced on the next tick. */
    private void markChanged() {
        setChanged();
        syncPending = true;
    }

    /** Markings change on interaction only, and the renderer needs them immediately. */
    public void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            // UPDATE_ALL, not UPDATE_CLIENTS: an adjacent tube has to re-evaluate the connection.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.load(tag.getCompound(TAG_ENERGY));
        if (sides.load(tag.getCompound(TAG_SIDES))) {
            Technomancy.LOGGER.warn("Fusor at {} could not restore its sides verbatim", worldPosition);
        }
        redstone.load(tag.getCompound(TAG_REDSTONE));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ENERGY, energy.save());
        tag.put(TAG_SIDES, sides.save());
        CompoundTag redstoneTag = new CompoundTag();
        redstone.save(redstoneTag);
        tag.put(TAG_REDSTONE, redstoneTag);
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
