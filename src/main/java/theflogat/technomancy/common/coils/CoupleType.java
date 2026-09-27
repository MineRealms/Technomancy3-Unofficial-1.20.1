package theflogat.technomancy.common.coils;

/**
 * What a coil moves, and therefore what kind of block it may be linked to.
 *
 * <p>Ported from {@code ICouplable.Type}. The original also declared {@code rf} and {@code fluid},
 * but no coil ever used them; they are left out rather than carried as dead constants.</p>
 */
public enum CoupleType {
    ITEM("item"),
    ESSENTIA("essentia");

    private final String id;

    CoupleType(String id) {
        this.id = id;
    }

    /** Stable id, used in translation keys. */
    public String id() {
        return id;
    }
}
