package theflogat.technomancy.compat.botania;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.common.blocks.botania.FlowerDynamoBlock;
import theflogat.technomancy.common.blocks.botania.ManaExchangerBlock;
import theflogat.technomancy.common.blocks.botania.ManaFabricatorBlock;
import theflogat.technomancy.common.blocks.machines.BoProcessorBlock;
import theflogat.technomancy.common.blocks.machines.ProcessorBlock;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.botania.FlowerDynamoBlockEntity;
import theflogat.technomancy.common.tiles.botania.ManaExchangerBlockEntity;
import theflogat.technomancy.common.tiles.botania.ManaFabricatorBlockEntity;
import theflogat.technomancy.common.tiles.machines.BoProcessorBlockEntity;

/**
 * The Botania module's registrations, kept in one place and installed only when Botania is
 * loaded.
 *
 * <p>Every class this touches links the Botania API at class load (the four block entities
 * implement Botania mana interfaces, and {@link ManaExchangerBlock} implements Botania's
 * {@code PoolOverlayProvider}), so nothing here may be referenced from a class that loads without
 * Botania. {@link theflogat.technomancy.Technomancy} calls {@link #install()} behind
 * {@link BotaniaPresence}, which is the only Botania entry point common code knows.</p>
 *
 * <p>The fields stay null when Botania is absent; the blocks, block items and block entity types
 * are simply never registered, and the gated recipes never load.</p>
 */
public final class BotaniaContent {

    public static RegistryObject<Block> FLOWER_DYNAMO;
    public static RegistryObject<Block> MANA_FABRICATOR;
    public static RegistryObject<Block> PROCESSOR_BO;
    public static RegistryObject<Block> MANA_EXCHANGER;

    public static RegistryObject<BlockEntityType<FlowerDynamoBlockEntity>> FLOWER_DYNAMO_BE;
    public static RegistryObject<BlockEntityType<ManaFabricatorBlockEntity>> MANA_FABRICATOR_BE;
    public static RegistryObject<BlockEntityType<BoProcessorBlockEntity>> PROCESSOR_BO_BE;
    public static RegistryObject<BlockEntityType<ManaExchangerBlockEntity>> MANA_EXCHANGER_BE;

    /** {@code ItemBOMaterial:0} mana coil, :1 manasteel gear. */
    public static RegistryObject<Item> MANA_COIL;
    public static RegistryObject<Item> MANASTEEL_GEAR;

    private BotaniaContent() {
    }

    /** Registers everything of the module. Only call this when {@link BotaniaPresence} is true. */
    public static void install() {
        FLOWER_DYNAMO = block("flower_dynamo",
                () -> new FlowerDynamoBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.WOOD)
                        .requiresCorrectToolForDrops().noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)));
        MANA_FABRICATOR = block("mana_fabricator",
                () -> new ManaFabricatorBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 6.0F).sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)));
        PROCESSOR_BO = block("processor_bo",
                () -> new BoProcessorBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 6.0F).sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().lightLevel(state ->
                                state.getValue(ProcessorBlock.LIT) ? ProcessorBlock.LIT_LIGHT : 0)));
        MANA_EXCHANGER = block("mana_exchanger",
                () -> new ManaExchangerBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 10.0F).sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().noOcclusion()
                        .isValidSpawn((state, level, pos, entity) -> false)));

        MANA_COIL = TechnomItems.ITEMS.register("mana_coil", () -> new Item(new Item.Properties()));
        MANASTEEL_GEAR = TechnomItems.ITEMS.register("manasteel_gear", () -> new Item(new Item.Properties()));

        FLOWER_DYNAMO_BE = TechnomBlockEntities.TYPES.register("flower_dynamo", () -> BlockEntityType.Builder
                .of(FlowerDynamoBlockEntity::new, FLOWER_DYNAMO.get()).build(null));
        MANA_FABRICATOR_BE = TechnomBlockEntities.TYPES.register("mana_fabricator", () -> BlockEntityType.Builder
                .of(ManaFabricatorBlockEntity::new, MANA_FABRICATOR.get()).build(null));
        PROCESSOR_BO_BE = TechnomBlockEntities.TYPES.register("processor_bo", () -> BlockEntityType.Builder
                .of(BoProcessorBlockEntity::new, PROCESSOR_BO.get()).build(null));
        MANA_EXCHANGER_BE = TechnomBlockEntities.TYPES.register("mana_exchanger", () -> BlockEntityType.Builder
                .of(ManaExchangerBlockEntity::new, MANA_EXCHANGER.get()).build(null));
    }

    /** The shared block registry's own helper also attaches the {@link BlockItem}. */
    private static RegistryObject<Block> block(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = TechnomBlocks.BLOCKS.register(name, block);
        TechnomItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
