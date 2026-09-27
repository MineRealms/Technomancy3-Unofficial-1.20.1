package theflogat.technomancy.common.coils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Item conservation of the coil's transfer, against full, partial, refusing and lying targets.
 * Stacks are modelled as (item, count) pairs so no Minecraft registry is needed.
 */
class CoilItemMoveTest {

    record Stack(String item, int count) {
        static final Stack EMPTY = new Stack("", 0);

        Stack withCount(int n) {
            return n <= 0 ? EMPTY : new Stack(item, n);
        }
    }

    /** A slotted inventory with IItemHandler semantics and a per-slot limit. */
    static class Inventory implements CoilItemMove.Source<Stack> {
        final Stack[] slots;
        final int limit;

        Inventory(int size, int limit) {
            slots = new Stack[size];
            java.util.Arrays.fill(slots, Stack.EMPTY);
            this.limit = limit;
        }

        int total(String item) {
            int sum = 0;
            for (Stack s : slots) {
                if (s.item().equals(item)) {
                    sum += s.count();
                }
            }
            return sum;
        }

        @Override
        public int slots() {
            return slots.length;
        }

        @Override
        public Stack extract(int slot, int amount, boolean simulate) {
            Stack s = slots[slot];
            int n = Math.min(amount, s.count());
            if (n <= 0) {
                return Stack.EMPTY;
            }
            if (!simulate) {
                slots[slot] = s.withCount(s.count() - n);
            }
            return s.withCount(n);
        }

        @Override
        public Stack insert(int slot, Stack stack, boolean simulate) {
            Stack s = slots[slot];
            if (stack.count() <= 0) {
                return Stack.EMPTY;
            }
            if (s.count() > 0 && !s.item().equals(stack.item())) {
                return stack;
            }
            int room = limit - s.count();
            int n = Math.min(room, stack.count());
            if (n <= 0) {
                return stack;
            }
            if (!simulate) {
                slots[slot] = new Stack(stack.item(), s.count() + n);
            }
            return stack.withCount(stack.count() - n);
        }

        @Override
        public Stack insertAnywhere(Stack stack) {
            Stack left = stack;
            for (int i = 0; i < slots.length && left.count() > 0; i++) {
                left = insert(i, left, false);
            }
            return left;
        }

        /** As a coil target: ItemHandlerHelper.insertItem over every slot. */
        Stack insertAll(Stack stack, boolean simulate) {
            Stack left = stack;
            for (int i = 0; i < slots.length && left.count() > 0; i++) {
                left = insert(i, left, simulate);
            }
            return left;
        }
    }

    private final List<Stack> dropped = new ArrayList<>();

    private int move(Inventory source, CoilItemMove.Target<Stack> target, Predicate<Stack> filter) {
        return CoilItemMove.moveOne(source, target, filter, Stack::count, dropped::add, 64);
    }

    private int move(Inventory source, Inventory target) {
        return move(source, target::insertAll, s -> true);
    }

    @Test
    void movesAWholeStackIntoAnEmptyTarget() {
        Inventory source = new Inventory(3, 64);
        Inventory target = new Inventory(2, 64);
        source.slots[1] = new Stack("stone", 40);
        assertEquals(40, move(source, target));
        assertEquals(0, source.total("stone"));
        assertEquals(40, target.total("stone"));
    }

    @Test
    void mergesIntoPartialStacksAndTakesOnlyWhatFits() {
        Inventory source = new Inventory(1, 64);
        Inventory target = new Inventory(1, 64);
        source.slots[0] = new Stack("stone", 30);
        target.slots[0] = new Stack("stone", 50);
        assertEquals(14, move(source, target));
        assertEquals(16, source.total("stone"));
        assertEquals(64, target.total("stone"));
        assertTrue(dropped.isEmpty());
    }

    @Test
    void aFullTargetTakesNothingAndTheSourceIsUntouched() {
        Inventory source = new Inventory(1, 64);
        Inventory target = new Inventory(1, 64);
        source.slots[0] = new Stack("stone", 10);
        target.slots[0] = new Stack("dirt", 64);
        assertEquals(0, move(source, target));
        assertEquals(10, source.total("stone"));
        assertEquals(64, target.total("dirt"));
    }

    @Test
    void skipsSlotsTheTargetCannotTakeAndMovesTheNextOne() {
        Inventory source = new Inventory(2, 64);
        Inventory target = new Inventory(1, 64);
        source.slots[0] = new Stack("dirt", 5);
        source.slots[1] = new Stack("stone", 5);
        target.slots[0] = new Stack("stone", 60);
        assertEquals(4, move(source, target));
        assertEquals(5, source.total("dirt"));
        assertEquals(1, source.total("stone"));
    }

    @Test
    void onlyOneSlotMovesPerCall() {
        Inventory source = new Inventory(2, 64);
        Inventory target = new Inventory(4, 64);
        source.slots[0] = new Stack("stone", 5);
        source.slots[1] = new Stack("dirt", 5);
        assertEquals(5, move(source, target));
        assertEquals(5, source.total("dirt"));
    }

    @Test
    void theFilterIsRespected() {
        Inventory source = new Inventory(2, 64);
        Inventory target = new Inventory(4, 64);
        source.slots[0] = new Stack("stone", 5);
        source.slots[1] = new Stack("dirt", 7);
        assertEquals(7, move(source, target::insertAll, s -> s.item().equals("dirt")));
        assertEquals(5, source.total("stone"));
        assertEquals(0, move(source, target::insertAll, s -> s.item().equals("dirt")));
    }

    @Test
    void aRefusingTargetCausesNoExtraction() {
        Inventory source = new Inventory(1, 64);
        source.slots[0] = new Stack("stone", 9);
        assertEquals(0, move(source, (stack, simulate) -> stack, s -> true));
        assertEquals(9, source.total("stone"));
    }

    /** A target whose simulation promised room it then refuses: everything goes back. */
    @Test
    void aLyingTargetLosesNothing() {
        Inventory source = new Inventory(1, 64);
        source.slots[0] = new Stack("stone", 20);
        CoilItemMove.Target<Stack> liar = (stack, simulate) -> simulate ? Stack.EMPTY : stack.withCount(stack.count() - 3);
        assertEquals(3, move(source, liar, s -> true));
        assertEquals(17, source.total("stone"));
        assertTrue(dropped.isEmpty());
    }

    /** If the source filled up meanwhile, the remainder goes elsewhere in it, then to the world. */
    @Test
    void unreturnableItemsAreDroppedNotDestroyed() {
        Inventory source = new Inventory(1, 64) {
            boolean taken;

            @Override
            public Stack extract(int slot, int amount, boolean simulate) {
                Stack out = super.extract(slot, amount, simulate);
                if (!simulate) {
                    taken = true;
                }
                return out;
            }

            @Override
            public Stack insert(int slot, Stack stack, boolean simulate) {
                return taken ? stack : super.insert(slot, stack, simulate);
            }
        };
        source.slots[0] = new Stack("stone", 20);
        CoilItemMove.Target<Stack> refuseOnExecute = (stack, simulate) -> simulate ? Stack.EMPTY : stack;
        assertEquals(0, move(source, refuseOnExecute, s -> true));
        assertEquals(List.of(new Stack("stone", 20)), dropped);
    }

    @Test
    void anEmptySourceMovesNothing() {
        assertEquals(0, move(new Inventory(3, 64), new Inventory(3, 64)));
    }
}
