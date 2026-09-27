package theflogat.technomancy.common.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Registry of optional energy protocols. Integrations register a factory during common
 * setup, and only after checking that their mod is loaded.
 */
public final class EnergyProtocols {

    private static final List<Function<MachineEnergy, EnergyProtocolExtension>> FACTORIES = new CopyOnWriteArrayList<>();

    private EnergyProtocols() {
    }

    public static void register(Function<MachineEnergy, EnergyProtocolExtension> factory) {
        FACTORIES.add(factory);
    }

    static List<EnergyProtocolExtension> createFor(MachineEnergy energy) {
        List<EnergyProtocolExtension> extensions = new ArrayList<>(FACTORIES.size());
        for (Function<MachineEnergy, EnergyProtocolExtension> factory : FACTORIES) {
            EnergyProtocolExtension extension = factory.apply(energy);
            if (extension != null) {
                extensions.add(extension);
            }
        }
        return extensions;
    }
}
