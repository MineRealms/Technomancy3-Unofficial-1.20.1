package theflogat.technomancy.common.tiles.botania;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import theflogat.technomancy.common.blocks.botania.FlowerDynamoBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.compat.gtceu.EuTier;

/**
 * {@code TileFlowerDynamo} (the "Hippie Dynamo"), ported 1:1 from
 * {@code TileDynamoBase} + {@code TileFlowerDynamo}.
 *
 * <p>Rates are the original's: a 100,000 Mana internal buffer, a 40,000 Q energy buffer with a
 * 320 Q/t ceiling, refuel once under 32 fuel, and each fuel unit burns for {@code calcEner()}
 * (80 Q, or 320 with the potency gem). One {@code extractFuel} call costs
 * {@code ceil(20 * calcEner / 80)} Mana and returns 160 fuel units, so one Mana is worth exactly
 * 640 Q either way — the gem is a throughput change only.</p>
 *
 * <p>It is a Botania {@link ManaReceiver}, as the original was: spreaders may fill it, and it
 * drains 100 Mana from every {@link ManaPool} in its 9x9 layer each tick.</p>
 */
public final class FlowerDynamoBlockEntity extends BlockEntity implements ManaReceiver {

    public static final int MAX_MANA = 100_000;
    public static final int DRAIN_PER_POOL = 100;
    public static final long ENERGY_CAPACITY = 40_000;
    public static final long MAX_EXTRACT = 320;
    public static final int BASE_RATE = 80;
    public static final int BOOSTED_RATE = 320;
    public static final int FUEL_PER_CHARGE = 160;
    public static final int FUEL_FLOOR = 32;
    public static final int DRAIN_RADIUS = 4;
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.HIGH;

    private static final String TAG_MANA = "Mana";
    private static final String TAG_FUEL = "Fuel";
    private static final String TAG_BOOST = "Boost";

    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);
    private final boolean euCapable;
    private int mana;
    private int fuel;
    private boolean boost;

    public FlowerDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(theflogat.technomancy.compat.botania.BotaniaContent.FLOWER_DYNAMO_BE.get(), pos, state);
        EnergyLimits limits = energyLimits();
        euCapable = limits.emitsEu();
        energy = new MachineEnergy(this, limits, energyPorts(facing(), euCapable));
    }

    /**
     * Energy shape of the dynamo: never accepts, emits at most {@link #MAX_EXTRACT} Q per tick.
     *
     * <p>Same shape as the other two dynamos, including why the EU rating is derived from the Q
     * budget rather than picked: a packet that does not fit the per-tick budget can never be
     * sent, and the EU protocol refuses an over-voltage packet outright instead of throttling
     * it. At the default four Q per EU that is two amperes of LV, i.e. 256 of the 320 Q/t. The
     * truncation of 320/128 = 2.5 to 2 is deliberate: {@code EnergyLedger.reservePackets} caps at
     * {@code floor(maxExtractPerTick / qPerPacket)} = 2 as well, so a third ampere would not move
     * one extra Q, only overstate the rating GT reads.</p>
     */
    private static EnergyLimits energyLimits() {
        EnergyLimits limits = EnergyLimits.fe(ENERGY_CAPACITY, 0, MAX_EXTRACT);
        long voltage = EuTier.LV.voltage();
        long amps = MAX_EXTRACT / (voltage * EnergyUnits.qPerEu());
        return amps > 0 ? limits.withEuOutput(voltage, amps) : limits;
    }

    /**
     * An EU-capable dynamo offers both protocols on its output face. One whose EU rating came out
     * at zero offers only FE: a face that advertises EU while its ledger refuses it would shadow
     * GTCEu's own {@code nativeEUToFE} wrapper, and {@code GtceuEnergyProtocol.push} answers such
     * a face with 0 rather than {@code NOT_APPLICABLE}, so the FE fallback would be skipped too
     * and a GT cable would receive nothing at all.
     */
    private static EnergyPorts energyPorts(Direction facing, boolean eu) {
        int mask = EnergyPorts.mask(facing);
        return eu ? EnergyPorts.generator(mask) : new EnergyPorts(0, mask, 0, 0);
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(FlowerDynamoBlock.FACING) ? state.getValue(FlowerDynamoBlock.FACING) : Direction.UP;
    }

    /** {@code TileDynamoBase.calcEner}. */
    public int calcEner() {
        return (int) Math.min(ENERGY_CAPACITY - energy.ledger().stored(), boost ? BOOSTED_RATE : BASE_RATE);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlowerDynamoBlockEntity dynamo) {
        if (dynamo.redstone.canRun(level, pos)) {
            if (dynamo.fuel < FUEL_FLOOR) {
                dynamo.fuel += dynamo.extractFuel(dynamo.calcEner());
            }
            int rate = dynamo.calcEner();
            if (dynamo.fuel != 0 && dynamo.energy.ledger().space() >= rate) {
                dynamo.energy.ledger().generate(rate);
                dynamo.fuel--;
                dynamo.setChanged();
            }
        }
        if (dynamo.mana <= MAX_MANA - DRAIN_PER_POOL) {
            dynamo.drainMana(level, pos);
        }
        dynamo.energy.pushOutput();
    }

    /** {@code TileFlowerDynamo.extractFuel}: Mana in, 160 fuel units out, or nothing. */
    public int extractFuel(int request) {
        int cost = (int) Math.ceil(20.0 * request / BASE_RATE);
        if (cost > mana) {
            return 0;
        }
        mana -= cost;
        setChanged();
        return FUEL_PER_CHARGE;
    }

    private void drainMana(Level level, BlockPos pos) {
        for (int dx = -DRAIN_RADIUS; dx <= DRAIN_RADIUS; dx++) {
            for (int dz = -DRAIN_RADIUS; dz <= DRAIN_RADIUS; dz++) {
                if (mana > MAX_MANA - DRAIN_PER_POOL) {
                    return;
                }
                BlockEntity neighbour = level.getBlockEntity(pos.offset(dx, 0, dz));
                if (neighbour == null) {
                    continue;
                }
                ManaReceiver receiver = neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, null)
                        .resolve().orElse(null);
                if (receiver instanceof ManaPool pool && pool.getCurrentMana() >= DRAIN_PER_POOL) {
                    pool.receiveMana(-DRAIN_PER_POOL);
                    mana += DRAIN_PER_POOL;
                    setChanged();
                }
            }
        }
    }

    // ---- Botania ManaReceiver ----

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
        return true;
    }

    // ---- upgrade, facing ----

    public boolean isBoosted() {
        return boost;
    }

    public boolean setBoosted(boolean installed) {
        if (boost == installed) {
            return false;
        }
        boost = installed;
        setChanged();
        return true;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public int mana() {
        return mana;
    }

    public Direction cycleFacing() {
        Direction next = Direction.from3DDataValue((facing().get3DDataValue() + 1) % Direction.values().length);
        if (level != null && getBlockState().hasProperty(FlowerDynamoBlock.FACING)) {
            level.setBlock(worldPosition, getBlockState().setValue(FlowerDynamoBlock.FACING, next), Block.UPDATE_ALL);
        }
        energy.setPorts(energyPorts(facing(), euCapable));
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
        tag.putInt(TAG_FUEL, fuel);
        tag.putBoolean(TAG_BOOST, boost);
        tag.put("Energy", energy.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mana = tag.getInt(TAG_MANA);
        fuel = tag.getInt(TAG_FUEL);
        boost = tag.getBoolean(TAG_BOOST);
        energy.load(tag.getCompound("Energy"));
        energy.setPorts(energyPorts(facing(), euCapable));
    }
}
