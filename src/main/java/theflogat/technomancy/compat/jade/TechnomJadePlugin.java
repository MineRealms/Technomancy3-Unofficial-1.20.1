package theflogat.technomancy.compat.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;
import theflogat.technomancy.common.tiles.essentia.EssentiaReservoirBlockEntity;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.BiomeMorpherBlockEntity;
import theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity;
import theflogat.technomancy.common.tiles.machines.ElectricBellowsBlockEntity;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;
import theflogat.technomancy.common.tiles.machines.EssentiaFusorBlockEntity;
import theflogat.technomancy.common.tiles.machines.ProcessorBlockEntity;
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistencePylonBlockEntity;

/**
 * Jade integration: the numbers a player currently has to open a GUI to see.
 *
 * <p>This class lives in common code on purpose. Jade loads its plugins on BOTH sides, so the
 * plugin may not touch a client class; the split Jade offers is used instead - the machine data
 * is collected in {@link TechnomJadeProvider#appendServerData} on the server and only rendered in
 * {@link TechnomJadeProvider#appendTooltip} on the client. That also means the client is told the
 * truth rather than reading its own possibly-stale copy.</p>
 *
 * <p>Discovered through Jade's {@code @WailaPlugin} annotation scan, so the class is never
 * touched when Jade is absent.</p>
 */
@WailaPlugin
public final class TechnomJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // One provider instance for every block entity that has something to say; the provider
        // decides what to send by looking at the block entity it was handed.
        for (Class<? extends BlockEntity> type : PROVIDED) {
            registration.registerBlockDataProvider(TechnomJadeProvider.INSTANCE, type);
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Registered against Block rather than each of our block classes: Jade matches
        // subclasses, and the provider bails out immediately on a block entity that is not one
        // of ours, so the cost is one instanceof per targeted block per frame.
        registration.registerBlockComponent(TechnomJadeProvider.INSTANCE, Block.class);
    }

    /** Every block entity the tooltip has something to show for. */
    private static final java.util.List<Class<? extends BlockEntity>> PROVIDED = java.util.List.of(
            EssentiaDynamoBlockEntity.class,
            EnergyCondenserBlockEntity.class,
            EssentiaFusorBlockEntity.class,
            ProcessorBlockEntity.class,
            ElectricBellowsBlockEntity.class,
            BiomeMorpherBlockEntity.class,
            EldritchConsumerBlockEntity.class,
            NodeDynamoBlockEntity.class,
            NodeFabricatorBlockEntity.class,
            QuantumJarBlockEntity.class,
            EssentiaReservoirBlockEntity.class,
            CreativeJarBlockEntity.class,
            CatalystBlockEntity.class,
            ExistenceFountainBlockEntity.class,
            ExistencePylonBlockEntity.class);
}
