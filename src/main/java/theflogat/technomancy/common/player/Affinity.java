package theflogat.technomancy.common.player;

/**
 * The five ritual affinities plus the unaligned fallback ({@code PlayerData.Affinity}).
 *
 * <p>The original's constructor was {@code private Affinity(int id) { id = ordinal(); }} — it
 * assigned the parameter, not the field, so every affinity kept {@code id == 0} and the lookup
 * was wrong. The ids here are the shared 0..4 order used by {@link Ritual.Type}, the crystal
 * variants and the catalyst variants, so there is exactly one numbering.</p>
 */
public enum Affinity {

    EARTH(0), FIRE(1), WATER(2), LIGHT(3), DARK(4), NORMAL(5);

    private final int id;

    Affinity(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Stable persistent-data key; never a display name. */
    public String key() {
        return "technom:affinity_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 24-bit RGB used by the HUD gauge. */
    public int color() {
        return switch (this) {
            case EARTH -> 0x55FF55;
            case FIRE -> 0xFF5555;
            case WATER -> 0x5555FF;
            case LIGHT -> 0xFFFFFF;
            case DARK -> 0x101010;
            default -> 0x2266AA;
        };
    }

    public static Affinity byId(int id) {
        for (Affinity affinity : values()) {
            if (affinity.id == id) {
                return affinity;
            }
        }
        return NORMAL;
    }
}
