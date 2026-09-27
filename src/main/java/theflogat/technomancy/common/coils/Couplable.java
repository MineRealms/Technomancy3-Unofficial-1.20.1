package theflogat.technomancy.common.coils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * A block entity the coil coupler can link from. Ported from {@code ICouplable}.
 *
 * <p>The original interface was just {@code addPos}/{@code clear}, and the coupler decided on its
 * own which blocks were acceptable targets through a class-name blacklist. Here the coil itself
 * answers {@link #acceptsLinkTarget}, so the rule that decides what may be linked and the rule
 * that decides what is actually used at transfer time are the same code.</p>
 */
public interface Couplable {

    CoupleType coupleType();

    /** The coil's links. Mutate only on the server, then call {@link #linksChanged()}. */
    CoilLinks links();

    /** Persists and syncs after the coupler edited {@link #links()}. */
    void linksChanged();

    /**
     * Whether the block at {@code target} is something this coil can draw from right now.
     * Server side; the caller has already checked that the position is loaded.
     *
     * @param face the face of {@code target} the player clicked
     */
    boolean acceptsLinkTarget(Level level, BlockPos target, Direction face);
}
