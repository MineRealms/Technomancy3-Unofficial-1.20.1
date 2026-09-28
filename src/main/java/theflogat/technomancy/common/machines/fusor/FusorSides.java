package theflogat.technomancy.common.machines.fusor;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The fusor's four horizontal slots: which two aspects go in, which one comes out, and how much
 * of each is buffered.
 *
 * <p>World-free on purpose, so the conservation rule can be unit tested: the only method that
 * moves essentia between the slots is {@link #fuse()}, it is all-or-nothing, and it takes exactly
 * one unit from each input for exactly one unit of output — the 2:1 exchange TC4 defines for a
 * compound aspect, which is the reason a fusor cannot multiply essentia.</p>
 *
 * <p>Ported from {@code TileEssentiaFusor}'s inner {@code SideInfo} map. The combination lookup
 * is injected as a {@link Combiner} because the real one reads the live aspect registry; in
 * production it is {@code AspectApi.combination}, which is the same unordered-pair lookup the
 * original open-coded by scanning every compound aspect ({@code getAspectCombo}, :129-140).</p>
 */
public final class FusorSides {

    /** {@code TileEssentiaFusor.MAX_AMOUNT}. */
    public static final int MAX_AMOUNT = 64;

    /** The four faces that can be marked; up and down are never slots. */
    public static final List<Direction> SLOTS =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    public enum SideType {
        NONE,
        INPUT,
        OUTPUT
    }

    /** Resolves the compound aspect two components make, or {@code null} if they make none. */
    @FunctionalInterface
    public interface Combiner {
        @Nullable
        AspectId combine(AspectId first, AspectId second);
    }

    /** What a cleared slot was, so the caller can hand the right item back. */
    public record Cleared(SideType type, @Nullable AspectId aspect) {
    }

    private static final String TAG_SIDES = "Sides";
    private static final String TAG_FACE = "face";
    private static final String TAG_TYPE = "type";
    private static final String TAG_ASPECT = "aspect";
    private static final String TAG_AMOUNT = "amount";

    private final EnumMap<Direction, SideType> types = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, AspectId> aspects = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> amounts = new EnumMap<>(Direction.class);

    public FusorSides() {
        SLOTS.forEach(face -> {
            types.put(face, SideType.NONE);
            amounts.put(face, 0);
        });
    }

    public static boolean isSlot(@Nullable Direction face) {
        return face != null && SLOTS.contains(face);
    }

    public SideType type(@Nullable Direction face) {
        return isSlot(face) ? types.get(face) : SideType.NONE;
    }

    @Nullable
    public AspectId aspect(@Nullable Direction face) {
        return isSlot(face) ? aspects.get(face) : null;
    }

    public int amount(@Nullable Direction face) {
        return isSlot(face) ? amounts.get(face) : 0;
    }

    public boolean isOccupied(@Nullable Direction face) {
        return type(face) != SideType.NONE;
    }

    /** Every unit the machine currently holds; the figure a conservation check compares. */
    public int totalStored() {
        int total = 0;
        for (Direction face : SLOTS) {
            total += amounts.get(face);
        }
        return total;
    }

    public boolean isEmpty() {
        return SLOTS.stream().allMatch(face -> types.get(face) == SideType.NONE) && totalStored() == 0;
    }

    public List<Direction> inputs() {
        List<Direction> inputs = new ArrayList<>(2);
        for (Direction face : SLOTS) {
            if (types.get(face) == SideType.INPUT) {
                inputs.add(face);
            }
        }
        return inputs;
    }

    @Nullable
    public Direction outputSide() {
        for (Direction face : SLOTS) {
            if (types.get(face) == SideType.OUTPUT) {
                return face;
            }
        }
        return null;
    }

    @Nullable
    public AspectId outputAspect() {
        Direction output = outputSide();
        return output == null ? null : aspects.get(output);
    }

    /** {@code fullyMarked()}: two inputs and an output, i.e. the machine has a recipe. */
    public boolean fullyMarked() {
        return inputs().size() == 2 && outputSide() != null;
    }

    // ---- marking ----

    /**
     * Marks {@code face} as the output, as an empty phial does.
     *
     * @return {@code true} if the slot was taken
     */
    public boolean markOutput(Direction face, Combiner combiner) {
        if (!isSlot(face) || isOccupied(face) || outputSide() != null) {
            return false;
        }
        types.put(face, SideType.OUTPUT);
        List<Direction> inputs = inputs();
        if (inputs.size() == 2) {
            // Marking the output last: the recipe is decided by the two inputs already there.
            aspects.put(face, combiner.combine(aspects.get(inputs.get(0)), aspects.get(inputs.get(1))));
        }
        return true;
    }

    /**
     * Marks {@code face} as an input of {@code aspect}, as a filled phial does.
     *
     * <p>The second input is refused unless the pair actually combines, which is what stops a
     * fusor from being configured into a recipe that can never run.</p>
     *
     * @return {@code true} if the slot was taken
     */
    public boolean markInput(Direction face, AspectId aspect, Combiner combiner) {
        if (!isSlot(face) || isOccupied(face) || aspect == null) {
            return false;
        }
        List<Direction> inputs = inputs();
        if (inputs.size() >= 2) {
            return false;
        }
        if (inputs.size() == 1) {
            AspectId combined = combiner.combine(aspect, aspects.get(inputs.get(0)));
            if (combined == null) {
                return false;
            }
            types.put(face, SideType.INPUT);
            aspects.put(face, aspect);
            Direction output = outputSide();
            if (output != null) {
                aspects.put(output, combined);
            }
            return true;
        }
        types.put(face, SideType.INPUT);
        aspects.put(face, aspect);
        return true;
    }

    /**
     * Unmarks a slot, which is only allowed while neither it nor the output holds anything — so
     * no buffered essentia can be stranded or lost by reconfiguring.
     *
     * @return what the slot was, or {@code null} if it may not be cleared
     */
    @Nullable
    public Cleared clear(Direction face) {
        if (!isSlot(face) || !isOccupied(face) || amount(face) != 0) {
            return null;
        }
        Direction output = outputSide();
        if (output != null && amounts.get(output) != 0) {
            return null;
        }
        Cleared cleared = new Cleared(types.get(face), aspects.get(face));
        types.put(face, SideType.NONE);
        aspects.remove(face);
        // The recipe is incomplete now, so the output forgets what it was going to make while
        // keeping its slot, exactly as the original did.
        if (output != null) {
            aspects.remove(output);
        }
        return cleared;
    }

    // ---- transfers ----

    /** Room left on one slot, or 0 if it cannot take this aspect. */
    public int space(Direction face, @Nullable AspectId aspect) {
        // Both an input and the output hold exactly one aspect and can be filled with it; only an
        // unmarked slot has no room. The output is normally filled by fuse(), but its capacity is
        // real, which is what lets a caller (or a test) inspect the blocked state.
        if (type(face) == SideType.NONE || aspect == null || !aspect.equals(aspect(face))) {
            return 0;
        }
        return MAX_AMOUNT - amount(face);
    }

    /** @return units accepted, in {@code [0, amount]} */
    public int add(Direction face, AspectId aspect, int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), space(face, aspect));
        if (accepted > 0 && !simulate) {
            amounts.merge(face, accepted, Integer::sum);
        }
        return accepted;
    }

    /** @return units removed, in {@code [0, amount]} */
    public int take(Direction face, @Nullable AspectId aspect, int amount, boolean simulate) {
        if (!isOccupied(face) || aspect == null || !aspect.equals(aspect(face))) {
            return 0;
        }
        int taken = Math.min(Math.max(0, amount), amount(face));
        if (taken > 0 && !simulate) {
            amounts.merge(face, -taken, Integer::sum);
        }
        return taken;
    }

    // ---- fusing ----

    /** Whether one fusion could run right now, resources aside. */
    public boolean canFuse() {
        if (!fullyMarked() || outputAspect() == null) {
            return false;
        }
        Direction output = outputSide();
        if (amounts.get(output) >= MAX_AMOUNT) {
            return false;
        }
        return inputs().stream().allMatch(face -> amounts.get(face) > 0);
    }

    /**
     * Turns one unit of each input into one unit of output.
     *
     * <p>Two units in, one out. The exchange is checked before anything moves and the total is
     * asserted afterwards, so no path through this class can create a unit.</p>
     *
     * @throws IllegalStateException if called when {@link #canFuse()} is false
     */
    public void fuse() {
        if (!canFuse()) {
            throw new IllegalStateException("fuse() called on a fusor that cannot fuse");
        }
        int before = totalStored();
        List<Direction> inputs = inputs();
        Direction output = outputSide();
        inputs.forEach(face -> amounts.merge(face, -1, Integer::sum));
        amounts.merge(output, 1, Integer::sum);
        int after = totalStored();
        if (after != before - 1) {
            throw new IllegalStateException("a fusion changed the total by " + (after - before)
                    + " instead of -1");
        }
    }

    // ---- persistence ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Direction face : SLOTS) {
            if (types.get(face) == SideType.NONE && amounts.get(face) == 0) {
                continue;
            }
            CompoundTag side = new CompoundTag();
            side.putByte(TAG_FACE, (byte) face.get3DDataValue());
            side.putByte(TAG_TYPE, (byte) types.get(face).ordinal());
            side.putInt(TAG_AMOUNT, amounts.get(face));
            AspectId aspect = aspects.get(face);
            if (aspect != null) {
                side.putString(TAG_ASPECT, aspect.serialized());
            }
            list.add(side);
        }
        if (!list.isEmpty()) {
            tag.put(TAG_SIDES, list);
        }
        return tag;
    }

    /**
     * Restores the slots, dropping anything unreadable rather than failing the block entity.
     *
     * @return {@code true} if the save could not be restored verbatim
     */
    public boolean load(CompoundTag tag) {
        SLOTS.forEach(face -> {
            types.put(face, SideType.NONE);
            amounts.put(face, 0);
        });
        aspects.clear();
        boolean lossy = false;
        ListTag list = tag.getList(TAG_SIDES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag side = list.getCompound(index);
            Direction face = Direction.from3DDataValue(side.getByte(TAG_FACE));
            SideType[] values = SideType.values();
            int ordinal = side.getByte(TAG_TYPE);
            if (!isSlot(face) || ordinal < 0 || ordinal >= values.length) {
                lossy = true;
                continue;
            }
            types.put(face, values[ordinal]);
            amounts.put(face, Math.max(0, Math.min(MAX_AMOUNT, side.getInt(TAG_AMOUNT))));
            if (amounts.get(face) != side.getInt(TAG_AMOUNT)) {
                lossy = true;
            }
            if (side.contains(TAG_ASPECT)) {
                try {
                    aspects.put(face, AspectId.parse(side.getString(TAG_ASPECT)));
                } catch (IllegalArgumentException malformed) {
                    lossy = true;
                }
            }
            // A slot with essentia but no aspect, or an aspect-less input, can never be used
            // again; drop the marking and keep nothing rather than stalling with a dead recipe.
            if (types.get(face) != SideType.NONE && aspects.get(face) == null
                    && types.get(face) == SideType.INPUT) {
                types.put(face, SideType.NONE);
                lossy = lossy || amounts.get(face) > 0;
                amounts.put(face, 0);
            }
        }
        return lossy;
    }

    /** Read-only view for renderers and probes. */
    public Map<Direction, SideType> typeView() {
        return java.util.Collections.unmodifiableMap(types);
    }
}
