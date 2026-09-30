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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import theflogat.technomancy.common.blocks.botania.ManaExchangerBlock;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomFluids;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;

/**
 * {@code TileManaExchanger}, ported 1:1: with a Botania mana pool directly above, it either turns
 * 1,000 Mana into 1,000 mB of mana fluid ({@code mode == false}) or the other way round
 * ({@code mode == true}), spending {@value #EXCHANGER_COST} Q of its 10,000 Q buffer per unit.
 *
 * <p>The face rules are the original's: energy arrives on any face but the top, and the fluid tank
 * only accepts or gives out on the sides, never on the top or through a side-less query, which is
 * the 1.20.1 form of "no {@code forgeDirection} means no access". A wrench flips the direction; it
 * runs while unpowered by default and can be reprogrammed like any other Technomancy machine.</p>
 *
 * <p>Upstream tested {@code tile instanceof TilePool}; 1.20.1 exposes mana pools through the
 * {@link ManaPool} capability, so any block presenting that capability above is accepted. The
 * mode and active flags live in the block state so the model and the pool overlay stay in sync
 * without a custom packet.</p>
 */
public final class ManaExchangerBlockEntity extends BlockEntity {

    /** {@code Rate.exchangerCost}: Q per 1,000 Mana or 1 mB. */
    public static final long EXCHANGER_COST = 1_000;
    /** {@code EnergyStorage(Rate.exchangerCost * 10)}. */
    public static final long ENERGY_CAPACITY = EXCHANGER_COST * 10;
    /** {@code new FluidTank(1000)}. */
    public static final int TANK_CAPACITY = 1_000;
    /** One operation swaps 1,000 Mana for one bucket. */
    public static final int MANA_PER_OPERATION = 1_000;
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.LOW;

    private static final String TAG_TANK = "Tank";
    private static final String TAG_ENERGY = "Energy";

    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(TechnomFluids.MANA.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    @SuppressWarnings("unchecked")
    private final LazyOptional<IFluidHandler>[] fluidViews = new LazyOptional[7];

    public ManaExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(theflogat.technomancy.compat.botania.BotaniaContent.MANA_EXCHANGER_BE.get(), pos, state);
        energy = new MachineEnergy(this,
                EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0),
                EnergyPorts.consumer(EnergyPorts.allExcept(Direction.UP)));
    }

    /**
     * The original's {@code mode}: {@code true} means the pool gains mana and the tank is drained
     * (fluid to mana), {@code false} means the pool is drained and the tank filled (mana to fluid).
     */
    public boolean mode() {
        BlockState state = getBlockState();
        return state.hasProperty(ManaExchangerBlock.OUT) && state.getValue(ManaExchangerBlock.OUT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaExchangerBlockEntity machine) {
        ManaPool pool = machine.redstone.canRun(level, pos) ? machine.poolAbove(level, pos) : null;
        boolean active = pool != null;
        if (active != state.getValue(ManaExchangerBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(ManaExchangerBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
        if (!active || machine.energy.ledger().stored() < EXCHANGER_COST) {
            return;
        }
        if (machine.mode()) {
            machine.fluidToMana(pool);
        } else {
            machine.manaToFluid(pool);
        }
    }

    private void fluidToMana(ManaPool pool) {
        if (tank.getFluidAmount() <= 0
                || pool.getCurrentMana() > pool.getMaxMana() - MANA_PER_OPERATION) {
            return;
        }
        if (energy.ledger().tryConsume(EXCHANGER_COST)) {
            pool.receiveMana(MANA_PER_OPERATION);
            tank.drain(1, IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
    }

    private void manaToFluid(ManaPool pool) {
        if (tank.getFluidAmount() >= tank.getCapacity() || pool.getCurrentMana() < MANA_PER_OPERATION) {
            return;
        }
        if (energy.ledger().tryConsume(EXCHANGER_COST)) {
            pool.receiveMana(-MANA_PER_OPERATION);
            tank.fill(new FluidStack(TechnomFluids.MANA.get(), 1), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
    }

    @Nullable
    private ManaPool poolAbove(Level level, BlockPos pos) {
        BlockEntity neighbour = level.getBlockEntity(pos.above());
        if (neighbour == null) {
            return null;
        }
        return neighbour.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, Direction.DOWN)
                .filter(ManaPool.class::isInstance).map(ManaPool.class::cast).orElse(null);
    }

    // ---- fluid: side-only, direction-gated ----

    /** The face rules of the original's {@code IFluidHandler}, expressed on the tank. */
    private final class ExposedTank implements IFluidHandler {

        @Nullable
        private final Direction side;

        ExposedTank(@Nullable Direction side) {
            this.side = side;
        }

        /** {@code from != UP}: the top is reserved for the pool, and {@code null} is no access. */
        private boolean onSide() {
            return side != null && side != Direction.UP;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // TileManaExchanger.fill: only while the machine is drinking its own tank (mode == true,
            // the fluid -> mana direction). The first cut had this and drain() the wrong way round,
            // so pipes could only ever take fluid out of a machine that was already consuming it.
            if (!onSide() || !mode() || !resource.getFluid().isSame(TechnomFluids.MANA.get())) {
                return 0;
            }
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.getFluid().isSame(TechnomFluids.MANA.get())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // TileManaExchanger.drain: only while the machine is producing fluid (mode == false).
            if (!onSide() || mode()) {
                return FluidStack.EMPTY;
            }
            return tank.drain(maxDrain, action);
        }
    }

    // ---- redstone, energy and lifecycle ----

    public RedstoneControl redstone() {
        return redstone;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // The wrapper reads mode live, so a wrench flipping it never leaves a stale view.
            int index = side == null ? 6 : side.get3DDataValue();
            LazyOptional<IFluidHandler> view = fluidViews[index];
            if (view == null) {
                view = LazyOptional.of(() -> new ExposedTank(side));
                fluidViews[index] = view;
            }
            return view.cast();
        }
        LazyOptional<T> energyView = energy.getCapability(cap, side);
        return energyView.isPresent() ? energyView : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
        for (int i = 0; i < fluidViews.length; i++) {
            if (fluidViews[i] != null) {
                fluidViews[i].invalidate();
                fluidViews[i] = null;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag tankTag = new CompoundTag();
        tank.writeToNBT(tankTag);
        tag.put(TAG_TANK, tankTag);
        tag.put(TAG_ENERGY, energy.save());
        redstone.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound(TAG_TANK));
        energy.load(tag.getCompound(TAG_ENERGY));
        redstone.load(tag.contains("redstone_mode") ? tag : null);
    }
}
