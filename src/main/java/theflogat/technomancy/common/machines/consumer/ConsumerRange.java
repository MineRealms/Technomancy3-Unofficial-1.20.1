package theflogat.technomancy.common.machines.consumer;

import net.minecraft.nbt.CompoundTag;

/**
 * The working volume of the eldritch consumer ({@code TileEldritchConsumer.Range}): a square
 * of half-width {@link #radius()} blocks scanned downward for {@link #height()} blocks, or all
 * the way down when the height is {@code -1}.
 *
 * <p>The set, the order and the ids are the original's; the id is what the old NBT tag
 * {@code RangeIdentificator} stored, so it is kept for readability of the tag only — this port
 * never reads a 1.7.10 save.</p>
 */
public enum ConsumerRange {

    LARGE(9, -1, 0, "Large"),
    SMALL(1, 1, 1, "Small"),
    TINY(0, 1, 2, "Tiny"),
    MEDIUM(5, -1, 3, "Medium"),
    AVERAGE(4, 9, 4, "Average"),
    GIGANTIC(16, -1, 5, "Gigantic");

    private static final String TAG = "range";

    private final int radius;
    private final int height;
    private final int id;
    private final String label;

    ConsumerRange(int radius, int height, int id, String label) {
        this.radius = radius;
        this.height = height;
        this.id = id;
        this.label = label;
    }

    /** Half-width of the scanned square, so the side is {@code 2 * radius + 1}. */
    public int radius() {
        return radius;
    }

    /** How far down to scan, or {@code -1} for "all the way to bedrock". */
    public int height() {
        return height;
    }

    public int id() {
        return id;
    }

    /** The lowest y that belongs to {@code originY}, inclusive. */
    public int lowestY(int originY, int minBuildHeight) {
        return height < 0 ? minBuildHeight : Math.max(minBuildHeight, originY - height - 1);
    }

    public String label() {
        return label;
    }

    /** The next range in the original {@code ValidRanges} order, wrapping around. */
    public ConsumerRange next() {
        ConsumerRange[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ConsumerRange byId(int id, ConsumerRange fallback) {
        for (ConsumerRange range : values()) {
            if (range.id == id) {
                return range;
            }
        }
        return fallback;
    }

    public void save(CompoundTag tag) {
        tag.putInt(TAG, id);
    }

    public static ConsumerRange load(CompoundTag tag, ConsumerRange fallback) {
        return byId(tag.getInt(TAG), fallback);
    }
}
