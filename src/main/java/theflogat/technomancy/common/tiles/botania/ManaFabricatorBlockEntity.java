package theflogat.technomancy.common.tiles.botania;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaReceiver;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileManaFabricator}: buys Mana with Forge Energy and pushes it into the Botania pools
 * around it. One Mana costs {@value #Q_PER_MANA} Q, which is the modern stand-in for the
 * original's TE rate; the buffer is 1,000,000 Mana and the rate is 100 a tick.
 */
public final class ManaFabricatorBlockEntity extends BlockEntity {

    public static final int MANA_CAPACITY = 1_000_000;
    public static final int MANA_RATE = 100;
    public static final long Q_PER_MANA = 100;

    private static final String TAG_MANA = "mana";

    private final MachineEnergy energy = new MachineEnergy(this,
            EnergyLimits.fe(MANA_CAPACITY * Q_PER_MANA, MANA_CAPACITY * Q_PER_MANA, 0),
            EnergyPorts.consumer(EnergyPorts.ALL));
    private int mana;

    public ManaFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.MANA_FABRICATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaFabricatorBlockEntity machine) {
        machine.make(level, pos);
        machine.push(level, pos);
    }

    private void make(Level level, BlockPos pos) {
        if (mana >= MANA_CAPACITY) {
            return;
        }
        long cost = Q_PER_MANA;
        if (energy.ledger().tryConsume(cost)) {
            mana = Math.min(MANA_CAPACITY, mana + 1);
            setChanged();
        }
    }

    private void push(Level level, BlockPos pos) {
        if (mana <= 0) {
            return;
        }
        for (Direction face : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(face));
            if (neighbour == null) {
                continue;
            }
            ManaReceiver receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, face.getOpposite())
                    .resolve().orElse(null);
            if (receiver == null || receiver.isFull()) {
                continue;
            }
            int before = receiver.getCurrentMana();
            receiver.receiveMana(Math.min(MANA_RATE, mana));
            int accepted = receiver.getCurrentMana() - before;
            if (accepted > 0) {
                mana -= accepted;
                setChanged();
            }
        }
    }

    public int mana() {
        return mana;
    }

    public MachineEnergy energy() {
        return energy;
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_MANA, mana);
        tag.put("Energy", energy.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mana = tag.getInt(TAG_MANA);
        energy.load(tag.getCompound("Energy"));
    }
}
