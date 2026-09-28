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
import vazkii.botania.api.mana.ManaPool;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileManaExchanger}: moves Mana and Forge Energy in either direction against an adjacent
 * Botania pool, one unit of Mana a tick each way at {@value #Q_PER_MANA} Q. It only converts when
 * the receiving account has room, so nothing is destroyed either way.
 */
public final class ManaExchangerBlockEntity extends BlockEntity {

    public static final long Q_PER_MANA = 80;
    public static final int MANA_PER_TICK = 1;
    public static final long ENERGY_CAPACITY = 40_000;
    private static final int PUSH_INTERVAL = 5;

    private final MachineEnergy energy = new MachineEnergy(this,
            EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, ENERGY_CAPACITY),
            new EnergyPorts(EnergyPorts.ALL, EnergyPorts.ALL, EnergyPorts.ALL, EnergyPorts.ALL));
    private int ticks;

    public ManaExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.MANA_EXCHANGER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaExchangerBlockEntity machine) {
        machine.ticks++;
        ManaPool pool = machine.pool(level, pos);
        if (pool == null) {
            machine.energy.pushOutput();
            return;
        }
        long unit = Q_PER_MANA;
        if (machine.energy.ledger().space() >= unit && pool.getCurrentMana() >= MANA_PER_TICK) {
            pool.receiveMana(-MANA_PER_TICK);
            machine.energy.ledger().generate(unit * MANA_PER_TICK);
            machine.setChanged();
        }
        if (machine.energy.ledger().stored() >= unit && !pool.isFull()) {
            if (machine.energy.ledger().tryConsume(unit)) {
                pool.receiveMana(MANA_PER_TICK);
                machine.setChanged();
            }
        }
        if (machine.ticks % PUSH_INTERVAL == 0) {
            machine.energy.pushOutput();
        }
    }

    @Nullable
    private ManaPool pool(Level level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(face));
            if (neighbour == null) {
                continue;
            }
            var receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, face.getOpposite())
                    .resolve().orElse(null);
            if (receiver instanceof ManaPool manaPool) {
                return manaPool;
            }
        }
        return null;
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
        tag.put("Energy", energy.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.load(tag.getCompound("Energy"));
    }
}
