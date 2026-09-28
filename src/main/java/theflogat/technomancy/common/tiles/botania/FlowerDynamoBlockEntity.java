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
 * {@code TileFlowerDynamo} (the "Hippie Dynamo"): drains Mana from the pools around it and turns
 * it into Forge Energy. One Mana yields {@value #Q_PER_MANA} Q; the buffer and output match the
 * essentia dynamo.
 */
public final class FlowerDynamoBlockEntity extends BlockEntity {

    public static final long ENERGY_CAPACITY = 40_000;
    public static final long Q_PER_MANA = 80;
    public static final int MANA_PER_TICK = 1;

    private static final String TAG_ENERGY = "Energy";

    private final MachineEnergy energy = new MachineEnergy(this,
            EnergyLimits.fe(ENERGY_CAPACITY, 0, 320), EnergyPorts.generator(EnergyPorts.ALL));

    public FlowerDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.FLOWER_DYNAMO.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlowerDynamoBlockEntity dynamo) {
        dynamo.burn(level, pos);
        dynamo.energy.pushOutput();
    }

    private void burn(Level level, BlockPos pos) {
        if (energy.ledger().space() < Q_PER_MANA) {
            return;
        }
        for (Direction face : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(face));
            if (neighbour == null) {
                continue;
            }
            ManaReceiver receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, face.getOpposite())
                    .resolve().orElse(null);
            if (receiver == null || receiver.getCurrentMana() < MANA_PER_TICK) {
                continue;
            }
            receiver.receiveMana(-MANA_PER_TICK);
            energy.ledger().generate(Q_PER_MANA * MANA_PER_TICK);
            setChanged();
            return;
        }
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
        energy.load(tag.getCompound(TAG_ENERGY));
    }
}
