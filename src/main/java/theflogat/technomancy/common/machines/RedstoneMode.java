package theflogat.technomancy.common.machines;

import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Three-state redstone control shared by the mod's machines: ignore redstone, run only while
 * powered, or run only while unpowered.
 *
 * <p>Ported from {@code IRedstoneSensitive.RedstoneSet}. The 1.7.10 original programmed the
 * mode by right-clicking with one of three vanilla items rather than through a GUI, and that
 * is kept — see {@link #byProgrammingItem}. What changed is the persisted form: the original
 * wrote the display string {@code "None"/"High"/"Low"} and fell back to {@code HIGH} whenever
 * it could not parse it, which would silently switch off any machine whose own default is
 * {@code LOW}. The saved form here is a stable lowercase id and an unreadable value falls back
 * to the caller's default instead.</p>
 */
public enum RedstoneMode {

    /** Redstone is ignored; the machine always runs. */
    NONE("none"),
    /** Runs only while the block receives a signal. */
    HIGH("high"),
    /** Runs only while the block receives no signal. */
    LOW("low");

    private static final RedstoneMode[] ORDER = values();

    private final String id;

    RedstoneMode(String id) {
        this.id = id;
    }

    /** Stable id used in NBT; never the enum name, so the constants can be renamed. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "technom.redstone_mode." + id;
    }

    /**
     * Whether a machine in this mode may work right now.
     *
     * @param powered result of {@code level.hasNeighborSignal(pos)}, i.e. 1.7.10's
     *                {@code isBlockIndirectlyGettingPowered}
     */
    public boolean canRun(boolean powered) {
        return switch (this) {
            case NONE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    /** Next mode in the original's {@code NONE, HIGH, LOW} order. */
    public RedstoneMode cycle() {
        return ORDER[(ordinal() + 1) % ORDER.length];
    }

    /**
     * The vanilla item that programs this mode. Resolved on call rather than stored in a field,
     * so loading this enum never touches the item registry.
     */
    public Item programmingItem() {
        return switch (this) {
            case NONE -> Items.GUNPOWDER;
            case HIGH -> Items.REDSTONE;
            case LOW -> Items.REDSTONE_TORCH;
        };
    }

    /** @return the mode this item programs, or {@code null} if it is not a programming item */
    @Nullable
    public static RedstoneMode byProgrammingItem(Item item) {
        for (RedstoneMode mode : ORDER) {
            if (mode.programmingItem() == item) {
                return mode;
            }
        }
        return null;
    }

    /** Reads a persisted {@link #id()}, falling back to the machine's own default. */
    public static RedstoneMode byId(String id, RedstoneMode fallback) {
        for (RedstoneMode mode : ORDER) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return fallback;
    }
}
