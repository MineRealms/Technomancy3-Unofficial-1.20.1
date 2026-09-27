package theflogat.technomancy.common.essentia;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * Which faces of a machine speak essentia, and in which direction.
 *
 * <p>Faces are viewed from the machine toward its neighbour, as the TC4R
 * {@link dev.tc4port.thaumcraft.api.essentia.EssentiaTransport} contract requires. A face that
 * is neither an input nor an output is not connectable at all, so tubes will not attach to it.</p>
 */
public record EssentiaPorts(int input, int output) {

    private static final int ALL_FACES = 0b111111;

    public static final EssentiaPorts NONE = new EssentiaPorts(0, 0);
    /** Every face both ways, e.g. a tube-like block. */
    public static final EssentiaPorts ALL = new EssentiaPorts(ALL_FACES, ALL_FACES);
    /** TC4's jar: only the top face, in both directions. */
    public static final EssentiaPorts TOP = both(Direction.UP);

    public EssentiaPorts {
        if ((input | output) != ((input | output) & ALL_FACES)) {
            throw new IllegalArgumentException("port mask has bits outside the six faces");
        }
    }

    /** Accepts essentia on the given faces only. */
    public static EssentiaPorts consumer(Direction... faces) {
        return new EssentiaPorts(mask(faces), 0);
    }

    /** Emits essentia on the given faces only. */
    public static EssentiaPorts producer(Direction... faces) {
        return new EssentiaPorts(0, mask(faces));
    }

    public static EssentiaPorts both(Direction... faces) {
        int mask = mask(faces);
        return new EssentiaPorts(mask, mask);
    }

    public static int mask(Direction... faces) {
        int mask = 0;
        for (Direction face : faces) {
            mask |= 1 << face.get3DDataValue();
        }
        return mask;
    }

    /** A {@code null} face is a side-less query and never carries transfer rights. */
    public boolean canInputFrom(@Nullable Direction face) {
        return has(input, face);
    }

    public boolean canOutputTo(@Nullable Direction face) {
        return has(output, face);
    }

    public boolean isConnectable(@Nullable Direction face) {
        return canInputFrom(face) || canOutputTo(face);
    }

    private static boolean has(int mask, @Nullable Direction face) {
        return face != null && (mask & 1 << face.get3DDataValue()) != 0;
    }
}
