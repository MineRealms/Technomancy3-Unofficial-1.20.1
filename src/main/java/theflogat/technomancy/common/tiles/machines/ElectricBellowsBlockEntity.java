package theflogat.technomancy.common.tiles.machines;

import javax.annotation.Nullable;
import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.block.entity.AlchemyFurnaceBlockEntity;
import dev.tc4port.thaumcraft.nativeimpl.mixin.FurnaceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.common.blocks.machines.ElectricBellowsBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.compat.gtceu.EuTier;
import theflogat.technomancy.compat.thaumcraft.ThaumcraftInternals;

/**
 * {@code TileElectricBellows}: an electric stand-in for Thaumcraft's hand-cranked bellows.
 *
 * <p>The original stoked whatever it faced — an alchemical furnace one block away, an arcane
 * furnace two blocks away, or a vanilla furnace — paying 3,000 RF to light one and then pushing
 * it along. The two-block reach and the 3,000 charge are kept; so is the {@code burnTime <= 2}
 * condition and the {@code vis < 50} headroom check, because boosting an alchemical furnace that
 * is already full of vis is what the original refused to do.</p>
 *
 * <p>The vanilla branch differs in one place and only because it has to: the original wrote 80
 * straight into the furnace's fuel counter, and 1.20.1 exposes cooking progress but not fuel.
 * One charge therefore buys a burst of eighty ticks during which cooking advances one step every
 * two ticks — the same forty steps the original's stoking produced, paid for the same way.</p>
 */
public final class ElectricBellowsBlockEntity extends BlockEntity implements EnergyHolder {

    /** {@code Rate.bellowsCost * 40}. */
    public static final long ENERGY_CAPACITY = 20_000;
    /** {@code Rate.bellowsCost * 6}: one stoking of an alchemical or vanilla furnace. */
    public static final long STOKE_COST = 3_000;
    /** {@code furnaceBurnTime = 80}. */
    public static final int FURNACE_BURN_TIME = 80;
    /** Original condition: it only stokes a furnace that has effectively run out. */
    public static final int MIN_BURN_TIME = 2;
    /** A vanilla furnace's whole cooking bar; the boost stops there and lets the block finish. */
    public static final int COOKING_TOTAL = 200;
    /** Every second tick of a burst advances cooking by one, as the original did. */
    private static final int PROGRESS_EVERY = 2;

    private static final long EU_INPUT_VOLTAGE = EuTier.EV.voltage();
    private static final long EU_INPUT_AMPS = 2;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_BOOST = "boost";

    /** Reused by the smeltability probe, so the tick allocates nothing. */
    private static final SimpleContainer smelting = new SimpleContainer(1);

    private final MachineEnergy energy;
    private int boost;

    public ElectricBellowsBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ELECTRIC_BELLOWS.get(), pos, state);
        energy = new MachineEnergy(this,
                EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0)
                        .withEuInput(EU_INPUT_VOLTAGE, EU_INPUT_AMPS),
                EnergyPorts.consumer(EnergyPorts.ALL));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricBellowsBlockEntity bellows) {
        bellows.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ElectricBellowsBlock.FACING);
        BlockEntity neighbour = level.getBlockEntity(pos.relative(facing));
        if (neighbour instanceof AlchemyFurnaceBlockEntity furnace) {
            stokeAlchemy(furnace);
            return;
        }
        // The original also reached two blocks for the arcane furnace. That machine has no
        // counterpart in TC4R, so the reach is not ported: one block, facing the furnace.
        if (neighbour instanceof AbstractFurnaceBlockEntity vanilla
                && neighbour instanceof FurnaceAccessor accessor) {
            stokeFurnace(level, vanilla, accessor);
        }
    }

    private void stokeAlchemy(AlchemyFurnaceBlockEntity furnace) {
        if (!ThaumcraftInternals.canStoke() || furnace.burnTime() > MIN_BURN_TIME
                || furnace.vis() >= AlchemyFurnaceBlockEntity.MAX_VIS) {
            return;
        }
        if (energy.ledger().stored() < STOKE_COST || !energy.ledger().tryConsume(STOKE_COST)) {
            return;
        }
        ThaumcraftInternals.stoke(furnace, FURNACE_BURN_TIME);
        furnace.setChanged();
        setChanged();
    }

    private void stokeFurnace(Level level, AbstractFurnaceBlockEntity vanilla, FurnaceAccessor furnace) {
        if (boost > 0) {
            boost--;
            if (level.getGameTime() % PROGRESS_EVERY == 0
                    && furnace.platform$progress() < COOKING_TOTAL) {
                furnace.platform$progress(furnace.platform$progress() + 1);
                vanilla.setChanged();
            }
            setChanged();
            return;
        }
        if (!smeltable(level, vanilla) || furnace.platform$progress() >= COOKING_TOTAL) {
            return;
        }
        if (energy.ledger().stored() < STOKE_COST || !energy.ledger().tryConsume(STOKE_COST)) {
            return;
        }
        boost = FURNACE_BURN_TIME;
        setChanged();
    }

    /** The original only paid for an input the furnace could actually smelt. */
    private static boolean smeltable(Level level, AbstractFurnaceBlockEntity furnace) {
        ItemStack input = furnace.getItem(0);
        if (input.isEmpty()) {
            return false;
        }
        smelting.setItem(0, input);
        boolean found = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, smelting, level).isPresent();
        smelting.setItem(0, ItemStack.EMPTY);
        return found;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public int boost() {
        return boost;
    }

    /**
     * Without this the bellows answered {@code LazyOptional.empty()} to every neighbour - Forge's
     * {@code BlockEntity} only exposes capabilities gathered from a registered provider field, and
     * {@link MachineEnergy} is not one - so it could never be charged and never stoked anything.
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
        tag.putInt(TAG_BOOST, boost);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.load(tag.getCompound(TAG_ENERGY));
        boost = tag.getInt(TAG_BOOST);
    }
}
