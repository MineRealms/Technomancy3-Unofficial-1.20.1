package theflogat.technomancy.common.energy;

/**
 * A block entity that stores energy. Every machine already exposes {@code public MachineEnergy
 * energy()}; this only gives that method a common type, so code that wants to read any machine's
 * buffer - the Jade tooltip provider, and anything else that would otherwise need one branch per
 * machine - can do it without reflection or an eleven-way {@code instanceof} chain.
 *
 * <p>Declaring it costs nothing: the classes already have the method with this exact signature.</p>
 */
public interface EnergyHolder {

    MachineEnergy energy();
}
