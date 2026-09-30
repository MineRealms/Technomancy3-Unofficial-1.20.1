package theflogat.technomancy.common.tiles.machines;

import javax.annotation.Nullable;
import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.block.TaintSpreadLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.common.blocks.machines.BiomeMorpherBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.machines.biome.BiomeTarget;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.compat.gtceu.EuRating;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * {@code TileBiomeMorpher}: trades energy for a slow, random rewrite of the biome around it.
 *
 * <p>The original converted one column per call, twice per {@code Rate.biomeMorpherCost} charge,
 * and picked the column from a triangular {@code rand(100) - rand(100)} offset, retrying once if
 * it landed on a column that already held the target biome. All of that is kept, including the
 * cost, the 800,000 Q buffer it buys forty charges with, and the "runs on a low signal"
 * redstone default.</p>
 *
 * <p>The one deliberate change is a loaded-chunk check: the original happily spent its charge on
 * columns outside the loaded area, where TC4R's conversion is a no-op. A morpher surrounded by
 * already-converted land still burns its charge, exactly as the original did.</p>
 */
public final class BiomeMorpherBlockEntity extends BlockEntity implements EnergyHolder {

    /** {@code Rate.biomeMorpherCost * 40}. */
    public static final long ENERGY_CAPACITY = 800_000;
    /** {@code RedstoneSet.LOW}: it runs while the signal is off. */
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.LOW;
    /** Columns converted per charge, as in the original's two {@code alterBiome} calls. */
    public static final int COLUMNS_PER_CHARGE = 2;
    /** The original's triangular {@code rand(100) - rand(100)} spread. */
    private static final int SPREAD = 100;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_REDSTONE = "Redstone";

    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);

    public BiomeMorpherBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.BIOME_MORPHER.get(), pos, state);
        energy = new MachineEnergy(this, limits(), EnergyPorts.consumer(EnergyPorts.ALL));
        redstone.setListener(this::setChanged);
    }

    /**
     * One whole charge is the dearest tick of work, and {@code serverTick} spends at most one per
     * tick, so that is the draw {@link EuRating} rates the machine against. The cost is read from
     * the config because that is the number the tick spends; the hardcoded EV rating this
     * replaces was below the 20,000 Q/t default, so an EU feed could not hold full rate and the
     * machine drained its buffer.
     */
    private static EnergyLimits limits() {
        EnergyLimits base = EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0);
        long voltage = EuRating.inputVoltage(TechnomancyConfig.BIOME_MORPHER_COST.get(), ENERGY_CAPACITY);
        return voltage > 0 ? base.withEuInput(voltage, EuRating.CONSUMER_AMPS) : base;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            BiomeMorpherBlockEntity morpher) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!morpher.redstone.canRun(level, pos)) {
            return;
        }
        long cost = TechnomancyConfig.BIOME_MORPHER_COST.get();
        if (morpher.energy.ledger().stored() < cost || !morpher.energy.ledger().tryConsume(cost)) {
            return;
        }
        for (int column = 0; column < COLUMNS_PER_CHARGE; column++) {
            morpher.convert(server);
        }
        morpher.setChanged();
    }

    private void convert(ServerLevel level) {
        BiomeTarget target = target();
        BlockPos origin = getBlockPos();
        for (int attempt = 0; attempt < 2; attempt++) {
            int dx = level.random.nextInt(SPREAD) - level.random.nextInt(SPREAD);
            int dz = level.random.nextInt(SPREAD) - level.random.nextInt(SPREAD);
            BlockPos at = origin.offset(dx, 0, dz);
            if (!level.isLoaded(at) || level.getBiome(at).is(target.biome())) {
                continue;
            }
            TaintSpreadLogic.setSpecialBiomeColumn(level, at, target.biome());
            return;
        }
    }

    public BiomeTarget target() {
        return getBlockState().getBlock() instanceof BiomeMorpherBlock
                ? getBlockState().getValue(BiomeMorpherBlock.TARGET) : BiomeTarget.MAGICAL_FOREST;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    /**
     * Forge's {@code BlockEntity} only answers a capability it gathered from a registered provider
     * field, and {@link MachineEnergy} is not one, so without this override the morpher answered
     * {@code LazyOptional.empty()} to every neighbour and could never be charged at all - not by
     * FE, not by EU. The other machines all delegate the same way.
     */
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
        tag.put(TAG_ENERGY, energy.save());
        redstone.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.load(tag.getCompound(TAG_ENERGY));
        redstone.load(tag);
    }
}
