package theflogat.technomancy.common.tiles.botania;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import theflogat.technomancy.common.blocks.botania.ManaFabricatorBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * {@code TileManaFabricator}, ported 1:1: it is a Botania {@link ManaPool} holding 100,000 Mana,
 * and buys 100 Mana for 1,000,000 Q. Energy only enters through the one face it is turned to,
 * and a wrench turns it, exactly as the original.
 */
public final class ManaFabricatorBlockEntity extends BlockEntity implements ManaPool {

    public static final int MAX_MANA = 100_000;
    public static final int MANA_PER_CYCLE = 100;
    public static final long FE_PER_CYCLE = 1_000_000;
    public static final long ENERGY_CAPACITY = FE_PER_CYCLE * 2;

    private static final String TAG_MANA = "Mana";

    private final MachineEnergy energy;
    private int mana;

    public ManaFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(theflogat.technomancy.compat.botania.BotaniaContent.MANA_FABRICATOR_BE.get(), pos, state);
        energy = new MachineEnergy(this, EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0),
                EnergyPorts.consumer(EnergyPorts.mask(facing())));
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(ManaFabricatorBlock.FACING) ? state.getValue(ManaFabricatorBlock.FACING) : Direction.UP;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaFabricatorBlockEntity machine) {
        // The 1.12 fork cut this to 5000; that is its own rebalance rather than upstream
        // consensus, so the original stays the default and the fork's number is a config value.
        long cost = TechnomancyConfig.MANA_FABRICATOR_COST.get();
        if (machine.energy.ledger().stored() >= cost && machine.mana + MANA_PER_CYCLE <= MAX_MANA) {
            if (machine.energy.ledger().tryConsume(cost)) {
                machine.mana += MANA_PER_CYCLE;
                machine.setChanged();
            }
        }
    }

    public MachineEnergy energy() {
        return energy;
    }

    // ---- Botania ManaPool ----

    @Override
    public Level getManaReceiverLevel() {
        return level;
    }

    @Override
    public BlockPos getManaReceiverPos() {
        return worldPosition;
    }

    @Override
    public int getCurrentMana() {
        return mana;
    }

    @Override
    public boolean isFull() {
        return mana >= MAX_MANA;
    }

    @Override
    public void receiveMana(int amount) {
        mana = Math.max(0, Math.min(MAX_MANA, mana + amount));
        setChanged();
    }

    @Override
    public boolean canReceiveManaFromBursts() {
        return false;
    }

    @Override
    public boolean isOutputtingPower() {
        return false;
    }

    @Override
    public int getMaxMana() {
        return MAX_MANA;
    }

    @Override
    public Optional<DyeColor> getColor() {
        return Optional.empty();
    }

    @Override
    public void setColor(Optional<DyeColor> color) {
    }

    public Direction cycleFacing() {
        Direction next = Direction.from3DDataValue((facing().get3DDataValue() + 1) % Direction.values().length);
        if (level != null && getBlockState().hasProperty(ManaFabricatorBlock.FACING)) {
            level.setBlock(worldPosition, getBlockState().setValue(ManaFabricatorBlock.FACING, next), Block.UPDATE_ALL);
        }
        energy.setPorts(EnergyPorts.consumer(EnergyPorts.mask(facing())));
        return facing();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == BotaniaForgeCapabilities.MANA_RECEIVER) {
            return BotaniaForgeCapabilities.MANA_RECEIVER.orEmpty(cap, LazyOptional.of(() -> this));
        }
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
        energy.setPorts(EnergyPorts.consumer(EnergyPorts.mask(facing())));
    }
}
