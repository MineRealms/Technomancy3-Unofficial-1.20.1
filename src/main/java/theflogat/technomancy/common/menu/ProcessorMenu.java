package theflogat.technomancy.common.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import theflogat.technomancy.common.registry.TechnomMenus;
import theflogat.technomancy.common.tiles.machines.ProcessorBlockEntity;

/**
 * The two-slot processor menu ({@code ContainerTCProcessor}), shared by every processor.
 *
 * <p>Slot positions are the original's: input at (50, 27), output at (107, 27). Progress and the
 * fuel level travel as {@link ContainerData}, so the block entity does not have to send a
 * block-entity packet to every nearby player on every tick the way the original did.</p>
 */
public class ProcessorMenu extends AbstractContainerMenu {

    private final IItemHandler items;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Block block;

    public ProcessorMenu(int id, Inventory playerInventory, IItemHandler items, ContainerData data,
            ContainerLevelAccess access, Block block) {
        super(TechnomMenus.PROCESSOR.get(), id);
        checkContainerDataCount(data, ProcessorBlockEntity.DATA_COUNT);
        this.items = items;
        this.data = data;
        this.access = access;
        this.block = block;

        addSlot(new SlotItemHandler(items, ProcessorBlockEntity.SLOT_INPUT, 50, 27));
        addSlot(new SlotItemHandler(items, ProcessorBlockEntity.SLOT_OUTPUT, 107, 27) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    /** Server side, bound to a real machine. */
    public static ProcessorMenu server(int id, Inventory playerInventory, ProcessorBlockEntity machine) {
        return new ProcessorMenu(id, playerInventory, machine.items(), machine.containerData(),
                ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos()),
                machine.getBlockState().getBlock());
    }

    /** Client side: the contents arrive through the ordinary menu synchronisation. */
    public static ProcessorMenu client(int id, Inventory playerInventory) {
        return new ProcessorMenu(id, playerInventory, new ItemStackHandler(ProcessorBlockEntity.SLOTS),
                new SimpleContainerData(ProcessorBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    /** Working ticks done, 0..{@code maxProgress}. */
    public int progress() {
        return data.get(ProcessorBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        return Math.max(1, data.get(ProcessorBlockEntity.DATA_MAX_PROGRESS));
    }

    public int fuel() {
        return data.get(ProcessorBlockEntity.DATA_FUEL);
    }

    public int fuelCapacity() {
        return data.get(ProcessorBlockEntity.DATA_FUEL_CAPACITY);
    }

    @Override
    public boolean stillValid(Player player) {
        return block == null || stillValid(access, player, block);
    }

    /**
     * Shift-clicking moves ores in and results out. A result is never pushed back into the
     * output slot, and the input slot's own filter decides what may go in.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = ProcessorBlockEntity.SLOTS;
        int playerEnd = slots.size();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (items.isItemValid(ProcessorBlockEntity.SLOT_INPUT, stack)) {
            if (!moveItemStackTo(stack, ProcessorBlockEntity.SLOT_INPUT,
                    ProcessorBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Between the player's inventory and the hotbar, as vanilla menus do.
            int hotbarStart = playerEnd - 9;
            boolean inHotbar = index >= hotbarStart;
            int from = inHotbar ? playerStart : hotbarStart;
            int to = inHotbar ? hotbarStart : playerEnd;
            if (!moveItemStackTo(stack, from, to, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
