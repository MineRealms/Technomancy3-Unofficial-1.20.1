package theflogat.technomancy.common.tiles.machines;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import theflogat.technomancy.common.blocks.machines.ProcessorBlock;
import theflogat.technomancy.common.machines.processing.OreProcessing;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.machines.processing.ProcessorCycle;
import theflogat.technomancy.common.machines.processing.SidedSlotView;
import theflogat.technomancy.common.menu.ProcessorMenu;

/**
 * Shared behaviour of the ore processors ({@code TileProcessorBase}): a two-slot inventory, a
 * 60-tick metered cycle, and the rule that nothing is paid for unless the result can be stored.
 *
 * <p>Only the resource is left to a subclass: essentia for the Thaumcraft processor, and for the
 * S3 Blood Magic and Botania ones their own networks. A subclass provides
 * {@link #module()} (which decides the pass record and therefore the reprocessing limit),
 * {@link #payTick(int)}, and the two numbers the menu shows.</p>
 */
public abstract class ProcessorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOTS = 2;

    /** Indices of {@link #containerData}; the menu reads them in this order. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_FUEL = 2;
    public static final int DATA_FUEL_CAPACITY = 3;
    public static final int DATA_COUNT = 4;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_CYCLE = "Cycle";

    protected final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Only the input slot carries the processing rule; the output slot is machine-managed
            // and the side-less view is the machine's own, so a pipe still cannot park an
            // unprocessable ore in the machine while the machine may fill either slot itself.
            return slot == SLOT_OUTPUT
                    || (slot == SLOT_INPUT && OreProcessing.plan(stack, module()).isPresent());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ProcessorCycle cycle = new ProcessorCycle();

    private final LazyOptional<IItemHandler> internalView = LazyOptional.of(() -> items);
    private final LazyOptional<IItemHandler> faceView =
            LazyOptional.of(() -> new SidedSlotView(items, SLOT_INPUT, SLOT_OUTPUT));

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> cycle.progress();
                case DATA_MAX_PROGRESS -> ProcessorCycle.TICKS;
                case DATA_FUEL -> fuelAmount();
                case DATA_FUEL_CAPACITY -> fuelCapacity();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; the client only ever reads these.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected ProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Which pass record this machine writes, and how often it may repeat itself. */
    public abstract ProcessingModule module();

    /**
     * Spends one working tick's worth of this machine's resource.
     *
     * <p>The whole {@link OreProcessing.Job} is handed over rather than just its
     * {@code tickCost}, because the two upstream processors price a tick differently: the
     * Thaumcraft one charges {@code max(1, multiplier + 2 * reprocess)} and the Botania one
     * {@code multiplier * 150 + 1500 * reprocess}. The shared cost unit cannot be scaled back
     * into those two, so each machine derives its own price from the stage and pass count.</p>
     *
     * @param job the planned job; never {@code null} when this is called
     * @return {@code true} if the whole cost was paid; a partial payment is never allowed
     */
    protected abstract boolean payTick(OreProcessing.Job job);

    /** Resource held, for the menu. */
    public abstract int fuelAmount();

    /** Resource capacity, for the menu. */
    public abstract int fuelCapacity();

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData containerData() {
        return containerData;
    }

    public int progress() {
        return cycle.progress();
    }

    /** Runs the inventory side of one tick. Subclasses call this from their own ticker. */
    protected void processTick(Level level, BlockPos pos, BlockState state) {
        Optional<OreProcessing.Job> planned = OreProcessing.plan(items.getStackInSlot(SLOT_INPUT), module());
        OreProcessing.Job job = planned.orElse(null);
        // Restored progress belongs to whatever is in the slot now; binding it before the first
        // tick is what stops a reload from resetting a nearly finished job.
        cycle.adopt(job);
        boolean fits = job != null && OreProcessing.fits(items.getStackInSlot(SLOT_OUTPUT), job);
        ProcessorCycle.Outcome outcome =
                cycle.tick(job, fits, job == null ? 1 : job.tickCost(), cost -> payTick(job));
        if (outcome == ProcessorCycle.Outcome.COMPLETED) {
            complete(job);
        }
        // Stays lit through a brief starvation, as the original's isActive did, so a machine fed
        // by a tube does not flicker its light and its model every other tick.
        boolean lit = switch (outcome) {
            case WORKED, COMPLETED -> true;
            case STARVED -> fits;
            case IDLE, BLOCKED -> false;
        };
        if (state.getValue(ProcessorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(ProcessorBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Moves one result into the output slot and consumes one input. */
    private void complete(OreProcessing.Job job) {
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, job.resultStack());
        } else {
            output.grow(1);
            items.setStackInSlot(SLOT_OUTPUT, output);
        }
        items.extractItem(SLOT_INPUT, 1, false);
    }

    /** Drops the inventory when the machine is broken; the loot table drops only the block. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return ProcessorMenu.server(id, playerInventory, this);
    }

    // ---- capabilities ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? internalView : faceView).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        internalView.invalidate();
        faceView.invalidate();
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        cycle.load(tag.getCompound(TAG_CYCLE));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.put(TAG_CYCLE, cycle.save());
    }
}
