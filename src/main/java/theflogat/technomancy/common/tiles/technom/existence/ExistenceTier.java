package theflogat.technomancy.common.tiles.technom.existence;

/** The pylon's three tiers ({@code TileExistencePylon.Type}): transfer rate and block kind. */
public enum ExistenceTier {
    BASIC(5), ADVANCED(25), COMPLEX(125);

    private final int rate;

    ExistenceTier(int rate) {
        this.rate = rate;
    }

    public int rate() {
        return rate;
    }
}
