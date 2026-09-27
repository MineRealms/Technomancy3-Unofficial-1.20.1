package theflogat.technomancy.common.energy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import theflogat.technomancy.Technomancy;

/**
 * Energy component of one machine block entity: the single {@link EnergyLedger}, its face
 * rules, the FE capability views and any optional protocol views (GT EU).
 *
 * <p>The owner forwards {@code getCapability}, {@code invalidateCaps} and save/load to this
 * component, and calls {@link #pushOutput} from its server tick if it is a generator.</p>
 */
public final class MachineEnergy {

    private static final String TAG_VERSION = "v";
    private static final String TAG_STORED = "q";
    private static final int SCHEMA_VERSION = 1;
    private static final int NULL_SIDE = 6;

    private final BlockEntity owner;
    private final EnergyLedger ledger;
    private EnergyPorts ports;

    @SuppressWarnings("unchecked")
    private final LazyOptional<IEnergyStorage>[] feViews = new LazyOptional[7];
    @Nullable
    private List<EnergyProtocolExtension> extensions;

    public MachineEnergy(BlockEntity owner, EnergyLimits limits, EnergyPorts ports) {
        this.owner = owner;
        this.ledger = new EnergyLedger(limits);
        this.ports = ports;
        this.ledger.setListener(owner::setChanged);
    }

    public EnergyLedger ledger() {
        return ledger;
    }

    public EnergyPorts ports() {
        return ports;
    }

    /** Changes the face rules and invalidates every handed-out capability so neighbours re-query. */
    public void setPorts(EnergyPorts ports) {
        if (!ports.equals(this.ports)) {
            this.ports = ports;
            invalidate();
        }
    }

    public BlockEntity owner() {
        return owner;
    }

    /** Server game time selecting the shared per-tick budget. */
    public long gameTime() {
        Level level = owner.getLevel();
        return level == null ? 0 : level.getGameTime();
    }

    /** Whether transfers may be committed now; always false on the client. */
    public boolean mutable() {
        Level level = owner.getLevel();
        return level != null && !level.isClientSide && !owner.isRemoved();
    }

    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return feView(side).cast();
        }
        for (EnergyProtocolExtension extension : extensions()) {
            LazyOptional<T> result = extension.getCapability(cap, side);
            if (result.isPresent()) {
                return result;
            }
        }
        return LazyOptional.empty();
    }

    private LazyOptional<IEnergyStorage> feView(@Nullable Direction side) {
        int index = side == null ? NULL_SIDE : side.get3DDataValue();
        LazyOptional<IEnergyStorage> view = feViews[index];
        if (view == null) {
            boolean input = side != null && ports.feIn(side);
            boolean output = side != null && ports.feOut(side);
            FeEnergyView storage = new FeEnergyView(ledger, this::gameTime, this::mutable, input, output);
            view = LazyOptional.of(() -> storage);
            feViews[index] = view;
        }
        return view;
    }

    private List<EnergyProtocolExtension> extensions() {
        if (extensions == null) {
            extensions = EnergyProtocols.createFor(this);
        }
        return extensions;
    }

    /** Invalidates all capabilities; new ones are created lazily on the next query. */
    public void invalidate() {
        for (int i = 0; i < feViews.length; i++) {
            if (feViews[i] != null) {
                feViews[i].invalidate();
                feViews[i] = null;
            }
        }
        if (extensions != null) {
            extensions.forEach(EnergyProtocolExtension::invalidate);
        }
    }

    /**
     * Sends stored energy to neighbours on every output face. A face prefers a native optional
     * protocol (GT EU) when the neighbour speaks it; FE is only used when no protocol applies.
     *
     * @return total Q sent
     */
    public long pushOutput() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide || ledger.stored() <= 0) {
            return 0;
        }
        BlockPos pos = owner.getBlockPos();
        long time = level.getGameTime();
        long sent = 0;
        for (Direction face : Direction.values()) {
            if (!ports.anyOutput(face) || ledger.stored() <= 0) {
                continue;
            }
            BlockPos target = pos.relative(face);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockEntity neighbour = level.getBlockEntity(target);
            if (neighbour == null || neighbour.isRemoved()) {
                continue;
            }
            long result = EnergyProtocolExtension.NOT_APPLICABLE;
            if (ports.euOut(face)) {
                for (EnergyProtocolExtension extension : extensions()) {
                    result = extension.push(neighbour, face, time);
                    if (result != EnergyProtocolExtension.NOT_APPLICABLE) {
                        break;
                    }
                }
            }
            if (result == EnergyProtocolExtension.NOT_APPLICABLE && ports.feOut(face)) {
                result = pushFe(neighbour, face, time);
            }
            if (result > 0) {
                sent += result;
            }
        }
        return sent;
    }

    private long pushFe(BlockEntity neighbour, Direction face, long time) {
        IEnergyStorage target = neighbour.getCapability(ForgeCapabilities.ENERGY, face.getOpposite()).orElse(null);
        if (target == null || !target.canReceive()) {
            return EnergyProtocolExtension.NOT_APPLICABLE;
        }
        try (EnergyLedger.Reservation reservation = ledger.reserveOutput(Integer.MAX_VALUE, time)) {
            if (reservation.amount() <= 0) {
                return 0;
            }
            int offered = (int) reservation.amount();
            int accepted = target.receiveEnergy(offered, false);
            return reservation.commit(Math.max(0, Math.min(accepted, offered)));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.putLong(TAG_STORED, ledger.storedForSave());
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.isEmpty()) {
            ledger.loadStored(0);
            return;
        }
        int version = tag.getInt(TAG_VERSION);
        if (version != SCHEMA_VERSION || !tag.contains(TAG_STORED, Tag.TAG_LONG)) {
            Technomancy.LOGGER.warn("Discarding energy data with unknown schema {} at {}", version, owner.getBlockPos());
            ledger.loadStored(0);
            return;
        }
        long saved = tag.getLong(TAG_STORED);
        if (ledger.loadStored(saved)) {
            Technomancy.LOGGER.warn("Clamped saved energy {} to [0, {}] at {}", saved, ledger.capacity(), owner.getBlockPos());
        }
    }
}
