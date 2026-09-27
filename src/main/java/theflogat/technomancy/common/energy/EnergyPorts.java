package theflogat.technomancy.common.energy;

import net.minecraft.core.Direction;

/**
 * Which faces of a machine allow FE/EU input and output. Faces are absolute world
 * directions; a rotatable machine rebuilds its ports when its facing changes.
 *
 * <p>A {@code null} side (internal or probe query) never grants transfer rights; it only
 * sees a read-only view. That keeps side-less queries from bypassing the face rules.</p>
 */
public record EnergyPorts(int feInput, int feOutput, int euInput, int euOutput) {

    public static final int ALL = 0b111111;
    public static final EnergyPorts NONE = new EnergyPorts(0, 0, 0, 0);

    public EnergyPorts {
        feInput &= ALL;
        feOutput &= ALL;
        euInput &= ALL;
        euOutput &= ALL;
    }

    /** Accepts FE and EU on the given faces. */
    public static EnergyPorts consumer(int inputFaces) {
        return new EnergyPorts(inputFaces, 0, inputFaces, 0);
    }

    /** Emits FE and EU on the given faces. */
    public static EnergyPorts generator(int outputFaces) {
        return new EnergyPorts(0, outputFaces, 0, outputFaces);
    }

    public static int mask(Direction... faces) {
        int mask = 0;
        for (Direction face : faces) {
            mask |= bit(face);
        }
        return mask;
    }

    /** Every face except the given ones. */
    public static int allExcept(Direction... faces) {
        return ALL & ~mask(faces);
    }

    public boolean feIn(Direction face) {
        return has(feInput, face);
    }

    public boolean feOut(Direction face) {
        return has(feOutput, face);
    }

    public boolean euIn(Direction face) {
        return has(euInput, face);
    }

    public boolean euOut(Direction face) {
        return has(euOutput, face);
    }

    public boolean anyOutput(Direction face) {
        return feOut(face) || euOut(face);
    }

    private static boolean has(int mask, Direction face) {
        return face != null && (mask & bit(face)) != 0;
    }

    private static int bit(Direction face) {
        return 1 << face.get3DDataValue();
    }
}
