package theflogat.technomancy.common.energy;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * An optional energy protocol (e.g. GT EU) attached to one {@link MachineEnergy}. Its
 * signature deliberately contains no third-party types, so machines and the shared energy
 * layer stay loadable when the protocol's mod is absent.
 */
public interface EnergyProtocolExtension {

    /** Returns the protocol's capability for {@code side}, or an empty optional when {@code cap} is not its own. */
    <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side);

    /**
     * Actively sends energy to {@code neighbour} through this protocol.
     *
     * @param face the machine face toward the neighbour
     * @return Q actually sent (possibly 0), or {@link #NOT_APPLICABLE} if the neighbour does not
     *         speak this protocol on that face. A neighbour that speaks it but refuses must yield
     *         0, which prevents falling back to another protocol on the same face.
     */
    long push(BlockEntity neighbour, Direction face, long gameTime);

    /** Invalidates every capability handed out so far. */
    void invalidate();

    long NOT_APPLICABLE = -1;
}
