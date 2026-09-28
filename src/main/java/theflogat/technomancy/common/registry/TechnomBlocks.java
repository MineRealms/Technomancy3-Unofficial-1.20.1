package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Supplier;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.dynamo.EssentiaDynamoBlock;
import theflogat.technomancy.common.blocks.essentia.CreativeJarBlock;
import theflogat.technomancy.common.blocks.essentia.EssentiaReservoirBlock;
import theflogat.technomancy.common.blocks.essentia.QuantumJarBlock;
import theflogat.technomancy.common.blocks.machines.AdvDeconTableBlock;
import theflogat.technomancy.common.blocks.machines.EldritchConsumerBlock;
import theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock;
import theflogat.technomancy.common.blocks.machines.EssentiaFusorBlock;
import theflogat.technomancy.common.blocks.machines.ProcessorBlock;
import theflogat.technomancy.common.blocks.machines.TcProcessorBlock;
import theflogat.technomancy.common.blocks.technom.BasaltBlock;
import theflogat.technomancy.common.blocks.technom.CatalystBlock;
import theflogat.technomancy.common.blocks.technom.CrystalBlock;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * Every block of the mod, plus the {@link BlockItem} that goes with it.
 *
 * <p>Registration is unconditional. The 1.7.10 original gated each block behind an
 * {@code Ids.*} boolean that also gated its research, which is how it ended up with research
 * entries that could never be reached; content is switched off with data-pack recipe
 * conditions instead, never by skipping registration.</p>
 */
public final class TechnomBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Technomancy.MOD_ID);

    /**
     * {@code cosmeticOpaque}. Purely decorative and a crafting ingredient — the original has
     * no block entity and no logic, only glass material, 0.25 hardness, translucent render
     * pass and a self drop, so vanilla {@link GlassBlock} is that behaviour exactly. It also
     * brings the two things the original lacked and 1.20 players expect: neighbouring faces
     * of the same block are not drawn, and skylight passes through.
     */
    public static final RegistryObject<Block> QUANTIZED_GLASS = register("quantized_glass",
            () -> new GlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.3F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    /**
     * {@code essentiaContainer}. Ten warded jars' worth of one aspect, with suction that
     * grows as it fills. See {@link theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity}.
     */
    public static final RegistryObject<Block> QUANTUM_JAR = register("quantum_jar",
            () -> new QuantumJarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    /**
     * {@code condenserBlock}. Spends Forge Energy to make potentia; see
     * {@link theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity}.
     */
    public static final RegistryObject<Block> ENERGY_CONDENSER = register("energy_condenser",
            () -> new EnergyCondenserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /**
     * {@code essentiaDynamo}. Burns essentia into Forge Energy. See
     * {@link theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity}.
     */
    public static final RegistryObject<Block> ESSENTIA_DYNAMO = register("essentia_dynamo",
            () -> new EssentiaDynamoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    // ---- S2 coils ----
    /** {@code itemTransmitter}: pulls from linked inventories into the one it stands on. */
    public static final RegistryObject<Block> ITEM_COIL = register("item_coil",
            () -> new theflogat.technomancy.common.blocks.coils.ItemCoilBlock(coilProperties()));
    /**
     * {@code teslaCoil}: wireless essentia from its linked stores into the block it stands on.
     * Registered upstream as {@code TMBlocks.teslaCoil}, implemented by
     * {@code BlockEssentiaTransmitter}.
     */
    public static final RegistryObject<Block> ESSENTIA_COIL = register("essentia_coil",
            () -> new theflogat.technomancy.common.blocks.coils.EssentiaCoilBlock(coilProperties()));
    // ---- end S2 coils ----

    // ---- S2 nodes, wands and fusion ----

    /**
     * {@code nodeDynamo}. Burns Vis drained from nearby aura nodes; see
     * {@link theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity}.
     */
    public static final RegistryObject<Block> NODE_DYNAMO = register("node_dynamo",
            () -> new theflogat.technomancy.common.blocks.nodes.NodeDynamoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    /**
     * {@code nodeGenerator}. The controller of a 1x3x3 node fabricator; see
     * {@link theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity}.
     */
    public static final RegistryObject<Block> NODE_FABRICATOR = register("node_fabricator",
            () -> new theflogat.technomancy.common.blocks.nodes.NodeFabricatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    // ---- S2 machines and storage ----

    /** {@code reservoir}. No recipe or research in the original either; creative tab only. */
    public static final RegistryObject<Block> ESSENTIA_RESERVOIR = register("essentia_reservoir",
            () -> new EssentiaReservoirBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    /** {@code creativeJar}. Unbreakable outside creative and no drops: see {@link CreativeJarBlock}. */
    public static final RegistryObject<Block> CREATIVE_JAR = register("creative_jar",
            () -> new CreativeJarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .noLootTable()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    /** {@code processorTC}: purifies ores for ignis. Lights up and smokes while working. */
    public static final RegistryObject<Block> PROCESSOR_TC = register("processor_tc",
            () -> new TcProcessorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ProcessorBlock.LIT) ? ProcessorBlock.LIT_LIGHT : 0)));

    /** {@code essentiaFusor}: combines two aspects into the compound they make. */
    public static final RegistryObject<Block> ESSENTIA_FUSOR = register("essentia_fusor",
            () -> new EssentiaFusorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    /**
     * {@code advDeconTable}: breaks an object down into a primal research point for its owner.
     * See {@link theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity}.
     */
    public static final RegistryObject<Block> ADV_DECON_TABLE = register("adv_decon_table",
            () -> new AdvDeconTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)));

    /**
     * {@code eldritchConsumer}: a powered pit that eats mobs, drops and blocks below it and
     * stores the aspect of what it destroys. See
     * {@link theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity}.
     */
    public static final RegistryObject<Block> ELDRITCH_CONSUMER = register("eldritch_consumer",
            () -> new EldritchConsumerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));

    /**
     * {@code fakeAirNG}. Registered without a {@link BlockItem} and without a loot table: a
     * fabricator places these and takes them away again, and nothing else can.
     */
    public static final RegistryObject<Block> NODE_FABRICATOR_SHELL = BLOCKS.register("node_fabricator_shell",
            () -> new theflogat.technomancy.common.blocks.nodes.NodeFabricatorShellBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(-1.0F, 3_600_000.0F)
                            .noLootTable()
                            .noCollission()
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, type) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)));

    // ---- end S2 nodes, wands and fusion ----

    // ---- S3 ritual core blocks ----

    /**
     * {@code crystalBlock}, {@code catalyst}, {@code basalt}: the ritual frame, the core and a
     * decorative rock. One block per kind rather than metadata; the kind order is
     * {@link theflogat.technomancy.common.rituals.Ritual.Type}.
     */
    public static final RegistryObject<Block> CRYSTAL_EARTH = crystal(Ritual.Type.EARTH);
    public static final RegistryObject<Block> CRYSTAL_FIRE = crystal(Ritual.Type.FIRE);
    public static final RegistryObject<Block> CRYSTAL_WATER = crystal(Ritual.Type.WATER);
    public static final RegistryObject<Block> CRYSTAL_LIGHT = crystal(Ritual.Type.LIGHT);
    public static final RegistryObject<Block> CRYSTAL_DARK = crystal(Ritual.Type.DARK);

    public static final RegistryObject<Block> CATALYST_EARTH = catalyst(Ritual.Type.EARTH);
    public static final RegistryObject<Block> CATALYST_FIRE = catalyst(Ritual.Type.FIRE);
    public static final RegistryObject<Block> CATALYST_WATER = catalyst(Ritual.Type.WATER);
    public static final RegistryObject<Block> CATALYST_LIGHT = catalyst(Ritual.Type.LIGHT);
    public static final RegistryObject<Block> CATALYST_DARK = catalyst(Ritual.Type.DARK);

    /** {@code basalt}: a plain block, ore-dictionary {@code basalt} in 1.7.10. */
    public static final RegistryObject<Block> BASALT = register("basalt",
            () -> new BasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    /**
     * {@code fakeAirLight}: an invisible light left behind by machines, with no item and no
     * drops. Like the fabricator shell it is registered without a {@link BlockItem}.
     */
    public static final RegistryObject<Block> FAKE_AIR_LIGHT = BLOCKS.register("fake_air_light",
            () -> new theflogat.technomancy.common.blocks.air.FakeAirLightBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(-1.0F, 3_600_000.0F)
                            .lightLevel(state -> 15)
                            .noLootTable()
                            .noCollission()
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, entity) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)));

    private static RegistryObject<Block> crystal(Ritual.Type type) {
        return register("crystal_" + type.name().toLowerCase(java.util.Locale.ROOT),
                () -> new CrystalBlock(type, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE)
                        .strength(0.3F)
                        .sound(SoundType.GLASS)
                        .lightLevel(state -> 1)
                        .noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)
                        .isRedstoneConductor((state, level, pos) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false)));
    }

    private static RegistryObject<Block> catalyst(Ritual.Type type) {
        return register("catalyst_" + type.name().toLowerCase(java.util.Locale.ROOT),
                () -> new CatalystBlock(type, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .strength(2.0F, 6.0F)
                        .sound(SoundType.STONE)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)));
    }

    // ---- end S3 ritual core blocks ----

    /** {@code fountainExistence}: the Existence fountain the dark ritual builds. */
    public static final RegistryObject<Block> EXISTENCE_FOUNTAIN = register("existence_fountain",
            () -> new theflogat.technomancy.common.blocks.technom.existence.ExistenceFountainBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.0F, 9.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, entity) -> false)));

    // ---- S3 Existence network ----

    public static final RegistryObject<Block> EXISTENCE_BURNER = register("existence_burner",
            () -> new theflogat.technomancy.common.blocks.technom.existence.ExistenceBurnerBlock(false,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 9.0F)
                            .sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> EXISTENCE_DYNAMIC_BURNER = register("existence_dynamic_burner",
            () -> new theflogat.technomancy.common.blocks.technom.existence.ExistenceBurnerBlock(true,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 9.0F)
                            .sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> EXISTENCE_PYLON_BASIC = pylon("existence_pylon_basic",
            theflogat.technomancy.common.tiles.technom.existence.ExistenceTier.BASIC);
    public static final RegistryObject<Block> EXISTENCE_PYLON_ADVANCED = pylon("existence_pylon_advanced",
            theflogat.technomancy.common.tiles.technom.existence.ExistenceTier.ADVANCED);
    public static final RegistryObject<Block> EXISTENCE_PYLON_COMPLEX = pylon("existence_pylon_complex",
            theflogat.technomancy.common.tiles.technom.existence.ExistenceTier.COMPLEX);

    private static RegistryObject<Block> pylon(String name,
            theflogat.technomancy.common.tiles.technom.existence.ExistenceTier tier) {
        return register(name, () -> new theflogat.technomancy.common.blocks.technom.existence.ExistencePylonBlock(tier,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)));
    }

    // ---- end S3 Existence network ----

    private TechnomBlocks() {
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        TechnomItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /** Shared by both coils (S2 coils group). */
    private static BlockBehaviour.Properties coilProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(Technomancy.MOD_ID, name);
    }
}
