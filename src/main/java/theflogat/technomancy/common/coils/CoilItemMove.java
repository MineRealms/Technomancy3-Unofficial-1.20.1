package theflogat.technomancy.common.coils;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Moves one slot's worth of items from a linked inventory into the coil's target, settling
 * with what the target actually accepted.
 *
 * <p>The original ({@code TileItemTransmitter.updateEntity}) found an empty target slot, wrote
 * the whole source stack into it and then set the source slot to {@code null}. It only ever
 * used empty slots, never merged into a partial stack, and trusted a raw {@code IInventory}
 * write. The order here is the one the port applies everywhere (engineering guide 6.3):
 * simulate the source, simulate the target, and only then take exactly what the target said it
 * would accept. If the target still refuses part of it - a foreign handler whose simulation lied
 * - the rest goes back to the source slot, then anywhere in the source, and only as a last
 * resort out into the world. Items are never destroyed and never duplicated.</p>
 *
 * <p>Generic over the stack type so the accounting is unit tested without Minecraft's item
 * registry; {@link theflogat.technomancy.common.tiles.coils.ItemCoilBlockEntity} binds it to
 * Forge's {@code IItemHandler} and {@code ItemStack}.</p>
 */
public final class CoilItemMove {

    /** An inventory the coil draws from. All methods follow {@code IItemHandler} semantics. */
    public interface Source<S> {
        int slots();

        S extract(int slot, int amount, boolean simulate);

        /** @return what did not fit */
        S insert(int slot, S stack, boolean simulate);

        /** Inserts into any slot; @return what did not fit */
        S insertAnywhere(S stack);
    }

    /** The block the coil faces. */
    @FunctionalInterface
    public interface Target<S> {
        /** @return what did not fit */
        S insert(S stack, boolean simulate);
    }

    private CoilItemMove() {
    }

    /**
     * Moves up to {@code maxPerSlot} items out of the first slot that has something the filter
     * allows and the target has room for.
     *
     * @param count      stack size of {@code S}; empty stacks have size 0
     * @param overflow   receives anything neither the target nor the source would take back
     * @return items that ended up in the target
     */
    public static <S> int moveOne(Source<S> source, Target<S> target, Predicate<S> filter,
            ToIntFunction<S> count, Consumer<S> overflow, int maxPerSlot) {
        for (int slot = 0; slot < source.slots(); slot++) {
            S offered = source.extract(slot, maxPerSlot, true);
            int available = count.applyAsInt(offered);
            if (available <= 0 || !filter.test(offered)) {
                continue;
            }
            int accepted = available - count.applyAsInt(target.insert(offered, true));
            if (accepted <= 0) {
                continue;
            }
            S taken = source.extract(slot, accepted, false);
            int takenCount = count.applyAsInt(taken);
            if (takenCount <= 0) {
                continue;
            }
            S left = target.insert(taken, false);
            int leftCount = count.applyAsInt(left);
            if (leftCount > 0) {
                S unreturned = source.insert(slot, left, false);
                if (count.applyAsInt(unreturned) > 0) {
                    unreturned = source.insertAnywhere(unreturned);
                }
                if (count.applyAsInt(unreturned) > 0) {
                    overflow.accept(unreturned);
                }
            }
            return Math.max(0, takenCount - leftCount);
        }
        return 0;
    }
}
