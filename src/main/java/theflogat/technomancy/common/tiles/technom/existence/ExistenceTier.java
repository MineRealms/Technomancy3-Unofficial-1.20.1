package theflogat.technomancy.common.tiles.technom.existence;

/**
 * The pylon's three tiers ({@code TileExistencePylon.Type}): transfer rate, block kind, and the
 * colour of the floating cube.
 *
 * <p>The original carried the tier in the block metadata and picked the cube colour in
 * {@code ModelExistencePylon.renderCube(int meta)} with a {@code glColor3f} per case. A 1.20.1
 * block model cannot call {@code glColor3f}, so the same three triples are exposed here as the
 * {@code 0xRRGGBB} values a block/item colour handler returns, and the model marks the cube's
 * faces with {@code "tintindex": 0}. {@code BASIC} is the original's meta 0, {@code ADVANCED} meta
 * 1 and {@code COMPLEX} meta 2, which is also the declaration order of the original's
 * {@code Type.allTypes}.</p>
 */
public enum ExistenceTier {
    /** Original {@code glColor3f(0, 0, 0.7F)}. */
    BASIC(5, 0x0000B3),
    /** Original {@code glColor3f(0, 0.5F, 0.5F)}. */
    ADVANCED(25, 0x008080),
    /** Original {@code glColor3f(0, 0.7F, 0)}. */
    COMPLEX(125, 0x00B300);

    private final int rate;
    private final int cubeTint;

    ExistenceTier(int rate, int cubeTint) {
        this.rate = rate;
        this.cubeTint = cubeTint;
    }

    public int rate() {
        return rate;
    }

    /** The {@code 0xRRGGBB} the floating cube is multiplied by. */
    public int cubeTint() {
        return cubeTint;
    }
}
