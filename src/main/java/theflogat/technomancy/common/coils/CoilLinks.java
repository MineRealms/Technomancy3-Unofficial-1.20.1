package theflogat.technomancy.common.coils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The remote endpoints of one coil: bounded, duplicate-free and position-only.
 *
 * <p>Replaces {@code TileCoilTransmitter.sources}, an unbounded {@code ArrayList} of coordinates
 * that accepted duplicates and the coil's own position, had no range limit, and was saved under
 * numbered keys ({@code xcoord0...}) that the 1.12 fork later corrupted into the literal string
 * {@code "pos.getX()0"}. The saved form here is a versioned list with fixed keys.</p>
 *
 * <p>Deliberately world-free, so every rule below is unit tested without a level.</p>
 */
public final class CoilLinks {

    /**
     * Farthest a link may reach, per axis (a cube, not a sphere). Matches TC4R's
     * {@code EssentiaSearch.MAX_RANGE}, the reach Thaumcraft itself allows remote essentia drains.
     * The original had no limit at all within a dimension.
     */
    public static final int MAX_RANGE = 32;
    /**
     * Most links one coil may hold. The original was unbounded; every link costs a block entity
     * lookup per tick, so a cap keeps a single coil from becoming a lag source.
     */
    public static final int MAX_LINKS = 16;

    static final int VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_LINKS = "links";
    private static final String TAG_FACE = "face";

    /** Outcome of an attempt to add a link. */
    public enum Result {
        ADDED,
        /** Target is the coil itself. */
        SELF,
        /** Target is farther than {@link #MAX_RANGE} on some axis. */
        OUT_OF_RANGE,
        /** Target is already linked. */
        DUPLICATE,
        /** The coil already has {@link #MAX_LINKS}. */
        FULL
    }

    private final List<CoilLink> links = new ArrayList<>();
    private int cursor;

    public static boolean inRange(BlockPos coil, BlockPos target) {
        return Math.abs(coil.getX() - target.getX()) <= MAX_RANGE
                && Math.abs(coil.getY() - target.getY()) <= MAX_RANGE
                && Math.abs(coil.getZ() - target.getZ()) <= MAX_RANGE;
    }

    /** Checks everything about a new link except whether the target block is of the right kind. */
    public Result check(BlockPos coil, BlockPos target) {
        if (coil.equals(target)) {
            return Result.SELF;
        }
        if (!inRange(coil, target)) {
            return Result.OUT_OF_RANGE;
        }
        if (contains(target)) {
            return Result.DUPLICATE;
        }
        return links.size() >= MAX_LINKS ? Result.FULL : Result.ADDED;
    }

    /** Adds the link if {@link #check} allows it. */
    public Result add(BlockPos coil, CoilLink link) {
        Result result = check(coil, link.pos());
        if (result == Result.ADDED) {
            links.add(link);
        }
        return result;
    }

    /** Duplicates are judged by position alone: two faces of one inventory would double-drain it. */
    public boolean contains(BlockPos target) {
        for (CoilLink link : links) {
            if (link.pos().equals(target)) {
                return true;
            }
        }
        return false;
    }

    public boolean remove(BlockPos target) {
        return links.removeIf(link -> link.pos().equals(target));
    }

    /** @return how many links were removed */
    public int clear() {
        int removed = links.size();
        links.clear();
        cursor = 0;
        return removed;
    }

    public int size() {
        return links.size();
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public List<CoilLink> view() {
        return Collections.unmodifiableList(links);
    }

    /**
     * The links in this pass's visiting order, starting one further along each call.
     *
     * <p>The original shuffled its list every tick so no source was always drained first. A
     * rotating start gives the same fairness deterministically, which keeps the transfer
     * testable. The returned list is a copy, so links may be removed while it is walked.</p>
     */
    public List<CoilLink> rotation() {
        int size = links.size();
        if (size == 0) {
            return List.of();
        }
        int start = Math.floorMod(cursor++, size);
        List<CoilLink> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(links.get((start + i) % size));
        }
        return order;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, VERSION);
        ListTag list = new ListTag();
        for (CoilLink link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", link.pos().getX());
            entry.putInt("y", link.pos().getY());
            entry.putInt("z", link.pos().getZ());
            if (link.face() != null) {
                entry.putString(TAG_FACE, link.face().getName());
            }
            list.add(entry);
        }
        tag.put(TAG_LINKS, list);
        return tag;
    }

    /**
     * Replaces the contents with a saved list, re-applying every rule a new link must pass.
     *
     * <p>A hand-edited or future-version save can therefore not smuggle in a self link, a
     * duplicate, an out-of-range link or more than {@link #MAX_LINKS}.</p>
     *
     * @return how many saved entries were rejected
     */
    public int load(@Nullable CompoundTag tag, BlockPos coil) {
        links.clear();
        cursor = 0;
        if (tag == null) {
            return 0;
        }
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        int rejected = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            Direction face = entry.contains(TAG_FACE, Tag.TAG_STRING)
                    ? Direction.byName(entry.getString(TAG_FACE))
                    : null;
            if (add(coil, new CoilLink(pos, face)) != Result.ADDED) {
                rejected++;
            }
        }
        return rejected;
    }
}
