package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;

/** Block entity types. */
public final class TechnomBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Technomancy.MOD_ID);

    public static final RegistryObject<BlockEntityType<QuantumJarBlockEntity>> QUANTUM_JAR =
            TYPES.register("quantum_jar", () -> BlockEntityType.Builder
                    .of(QuantumJarBlockEntity::new, TechnomBlocks.QUANTUM_JAR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<EnergyCondenserBlockEntity>> ENERGY_CONDENSER =
            TYPES.register("energy_condenser", () -> BlockEntityType.Builder
                    .of(EnergyCondenserBlockEntity::new, TechnomBlocks.ENERGY_CONDENSER.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<EssentiaDynamoBlockEntity>> ESSENTIA_DYNAMO =
            TYPES.register("essentia_dynamo", () -> BlockEntityType.Builder
                    .of(EssentiaDynamoBlockEntity::new, TechnomBlocks.ESSENTIA_DYNAMO.get())
                    .build(null));

    // ---- S2 nodes, wands and fusion ----

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity>> NODE_DYNAMO =
            TYPES.register("node_dynamo", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity::new, TechnomBlocks.NODE_DYNAMO.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity>> NODE_FABRICATOR =
            TYPES.register("node_fabricator", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity::new,
                            TechnomBlocks.NODE_FABRICATOR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.nodes.NodeFabricatorShellBlockEntity>> NODE_FABRICATOR_SHELL =
            TYPES.register("node_fabricator_shell", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.nodes.NodeFabricatorShellBlockEntity::new,
                            TechnomBlocks.NODE_FABRICATOR_SHELL.get())
                    .build(null));

    // ---- end S2 nodes, wands and fusion ----

    private TechnomBlockEntities() {
    }
}
