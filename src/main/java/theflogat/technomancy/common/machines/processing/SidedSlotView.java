package theflogat.technomancy.common.machines.processing;

import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * The face view of a machine inventory: one slot may only be filled, another may only be
 * emptied.
 *
 * <p>This is the fix for the defect that made the processors unusable with automation. The base
 * class listed both slots for every side ({@code TileProcessorBase.getAccessibleSlotsFromSide},
 * :190) but the 1.12 subclasses overrode it with an empty array, so hoppers and pipes could not
 * reach either slot ({@code TileBMProcessor.java:43}, {@code TileBOProcessor.java:100}). The
 * insert/extract rules are the base class's own ({@code canInsertItem}: slot 0,
 * {@code canExtractItem}: slot 1), enforced here per slot instead of per side.</p>
 *
 * <p>A {@code null} side query gets the undecorated handler instead of this view, so the
 * machine's own code and its menu are not bound by the automation rules — and, the other way
 * round, no automation can use the side-less query to bypass them.</p>
 */
public final class SidedSlotView implements IItemHandler {

    private final IItemHandler delegate;
    private final int insertSlot;
    private final int extractSlot;

    /** @param insertSlot the only insertable slot, or {@code -1} for none; same for extract */
    public SidedSlotView(IItemHandler delegate, int insertSlot, int extractSlot) {
        this.delegate = delegate;
        this.insertSlot = insertSlot;
        this.extractSlot = extractSlot;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return slot == insertSlot ? delegate.insertItem(slot, stack, simulate) : stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return slot == extractSlot ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return slot == insertSlot && delegate.isItemValid(slot, stack);
    }
}
