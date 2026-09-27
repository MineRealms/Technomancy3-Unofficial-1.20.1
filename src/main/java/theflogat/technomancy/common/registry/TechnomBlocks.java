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
import theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock;
import theflogat.technomancy.common.blocks.machines.ProcessorBlock;
import theflogat.technomancy.common.blocks.machines.TcProcessorBlock;

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

    private TechnomBlocks() {
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        TechnomItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(Technomancy.MOD_ID, name);
    }
}
