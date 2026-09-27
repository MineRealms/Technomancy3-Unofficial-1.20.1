package theflogat.technomancy.common.tiles.base;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The three ways a machine can answer a redstone signal, as the 1.7.10 {@code RedstoneSet} did.
 *
 * <p>The three "programming" items are kept verbatim, because they are the entire user
 * interface of these machines: there is no GUI, so right-clicking a machine with gunpowder,
 * redstone dust or a redstone torch is how the mode is set. The original also had a
 * {@code cycle()} that nothing ever called; it is not reproduced.</p>
 */
public enum RedstoneMode implements StringRepresentable {

    /** Ignores redstone entirely. */
    NONE("none", Items.GUNPOWDER),
    /** Runs only while powered. */
    HIGH("high", Items.REDSTONE),
    /** Runs only while unpowered. */
    LOW("low", Items.REDSTONE_TORCH);

    private static final RedstoneMode[] ORDER = values();

    private final String name;
    private final Item programmingItem;

    RedstoneMode(String name, Item programmingItem) {
        this.name = name;
        this.programmingItem = programmingItem;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The item that selects this mode, and that is handed back when the mode is replaced. */
    public Item programmingItem() {
        return programmingItem;
    }

    /**
     * Whether a machine in this mode may work right now.
     *
     * <p>{@code hasNeighborSignal} is the 1.20.1 equivalent of
     * {@code isBlockIndirectlyGettingPowered}: it counts a signal arriving through an adjacent
     * block, not just a wire touching this one.</p>
     */
    public boolean canRun(Level level, BlockPos pos) {
        return switch (this) {
            case NONE -> true;
            case HIGH -> level.hasNeighborSignal(pos);
            case LOW -> !level.hasNeighborSignal(pos);
        };
    }

    /**
     * Parses a saved name.
     *
     * @param fallback returned for anything unrecognised, which must be the owning machine's
     *                 own default rather than a globally fixed mode - the original hard-coded
     *                 {@code HIGH} here and so silently switched a machine's behaviour whenever
     *                 the key was missing
     */
    public static RedstoneMode byName(@Nullable String name, RedstoneMode fallback) {
        if (name != null) {
            for (RedstoneMode mode : ORDER) {
                if (mode.name.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return fallback;
    }

    /** The mode a held item selects, if it is one of the three programming items. */
    public static Optional<RedstoneMode> forItem(Item item) {
        for (RedstoneMode mode : ORDER) {
            if (mode.programmingItem == item) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
