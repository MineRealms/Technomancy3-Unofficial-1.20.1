package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;
import theflogat.technomancy.common.tiles.essentia.EssentiaReservoirBlockEntity;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;
import theflogat.technomancy.common.tiles.machines.EssentiaFusorBlockEntity;
import theflogat.technomancy.common.tiles.machines.TcProcessorBlockEntity;

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

    // ---- S2 coils ----
    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.coils.ItemCoilBlockEntity>> ITEM_COIL =
            TYPES.register("item_coil", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.coils.ItemCoilBlockEntity::new, TechnomBlocks.ITEM_COIL.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.coils.EssentiaCoilBlockEntity>> ESSENTIA_COIL =
            TYPES.register("essentia_coil", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.coils.EssentiaCoilBlockEntity::new, TechnomBlocks.ESSENTIA_COIL.get())
                    .build(null));
    // ---- end S2 coils ----

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

    // ---- S2 machines and storage ----

    public static final RegistryObject<BlockEntityType<EssentiaReservoirBlockEntity>> ESSENTIA_RESERVOIR =
            TYPES.register("essentia_reservoir", () -> BlockEntityType.Builder
                    .of(EssentiaReservoirBlockEntity::new, TechnomBlocks.ESSENTIA_RESERVOIR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<CreativeJarBlockEntity>> CREATIVE_JAR =
            TYPES.register("creative_jar", () -> BlockEntityType.Builder
                    .of(CreativeJarBlockEntity::new, TechnomBlocks.CREATIVE_JAR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TcProcessorBlockEntity>> PROCESSOR_TC =
            TYPES.register("processor_tc", () -> BlockEntityType.Builder
                    .of(TcProcessorBlockEntity::new, TechnomBlocks.PROCESSOR_TC.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<EssentiaFusorBlockEntity>> ESSENTIA_FUSOR =
            TYPES.register("essentia_fusor", () -> BlockEntityType.Builder
                    .of(EssentiaFusorBlockEntity::new, TechnomBlocks.ESSENTIA_FUSOR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity>> ADV_DECON_TABLE =
            TYPES.register("adv_decon_table", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity::new,
                            TechnomBlocks.ADV_DECON_TABLE.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity>> ELDRITCH_CONSUMER =
            TYPES.register("eldritch_consumer", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity::new,
                            TechnomBlocks.ELDRITCH_CONSUMER.get())
                    .build(null));

    // ---- S3 ritual core blocks ----

    /** One type for the five catalyst blocks; the block itself carries the kind. */
    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.technom.CatalystBlockEntity>> CATALYST =
            TYPES.register("catalyst", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.technom.CatalystBlockEntity::new,
                            TechnomBlocks.CATALYST_EARTH.get(), TechnomBlocks.CATALYST_FIRE.get(),
                            TechnomBlocks.CATALYST_WATER.get(), TechnomBlocks.CATALYST_LIGHT.get(),
                            TechnomBlocks.CATALYST_DARK.get())
                    .build(null));

    // ---- end S3 ritual core blocks ----

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.air.FakeAirLightBlockEntity>> FAKE_AIR_LIGHT =
            TYPES.register("fake_air_light", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.air.FakeAirLightBlockEntity::new,
                            TechnomBlocks.FAKE_AIR_LIGHT.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity>> EXISTENCE_FOUNTAIN =
            TYPES.register("existence_fountain", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity::new,
                            TechnomBlocks.EXISTENCE_FOUNTAIN.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.technom.existence.ExistenceBurnerBlockEntity>> EXISTENCE_BURNER =
            TYPES.register("existence_burner", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.technom.existence.ExistenceBurnerBlockEntity::new,
                            TechnomBlocks.EXISTENCE_BURNER.get(), TechnomBlocks.EXISTENCE_DYNAMIC_BURNER.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.technom.existence.ExistencePylonBlockEntity>> EXISTENCE_PYLON =
            TYPES.register("existence_pylon", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.technom.existence.ExistencePylonBlockEntity::new,
                            TechnomBlocks.EXISTENCE_PYLON_BASIC.get(), TechnomBlocks.EXISTENCE_PYLON_ADVANCED.get(),
                            TechnomBlocks.EXISTENCE_PYLON_COMPLEX.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<theflogat.technomancy.common.tiles.technom.existence.ExistenceUserBlockEntity>> EXISTENCE_USER =
            TYPES.register("existence_user", () -> BlockEntityType.Builder
                    .of(theflogat.technomancy.common.tiles.technom.existence.ExistenceUserBlockEntity::new,
                            TechnomBlocks.EXISTENCE_CROP_ACCELERATOR.get(), TechnomBlocks.EXISTENCE_HARVESTER.get(),
                            TechnomBlocks.EXISTENCE_SEALER.get())
                    .build(null));

    // The Botania machines' block entity types are registered from
    // compat/botania/BotaniaContent, only when Botania is loaded.

    private TechnomBlockEntities() {
    }
}
