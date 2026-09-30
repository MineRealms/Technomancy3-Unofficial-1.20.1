package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.research.ResearchCatalog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyHolder;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * S4 deep Thaumcraft: the flux lamp, the electric bellows and the biome morpher, plus the two
 * rules that any block of this mod has to keep - a block that demands a correct tool must be in a
 * mineable tag, and content that was added must have a recipe and a research entry.
 *
 * <p>The first test is deliberately about <em>every</em> block of the mod rather than these three:
 * the whole tree had grown 21 tool-requiring blocks that belonged to no mineable tag, so placing
 * one cost the player the block. A per-block assertion would have missed the next one.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S4DeepTcGameTests {

    private static final String BATCH = "technom_s4_deep_tc";

    private S4DeepTcGameTests() {
    }

    /**
     * A block with {@code requiresCorrectToolForDrops()} that is in no mineable tag cannot be
     * harvested with anything: the tool check never passes and the loot table never runs.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyToolRequiringBlockIsMineable(GameTestHelper helper) {
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!Technomancy.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            if (!state.requiresCorrectToolForDrops()) {
                continue;
            }
            checked++;
            S2StorageGameTests.check(helper, mineable(state),
                    "technom:" + id.getPath() + " requires a correct tool but is in no mineable tag,"
                            + " so it can never drop");
        }
        S2StorageGameTests.check(helper, checked > 0, "no technom block requires a tool at all");
        Technomancy.LOGGER.info("GameTest S4: {} tool-requiring blocks are all mineable", checked);
        helper.succeed();
    }

    private static boolean mineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE);
    }

    /** Each S4 machine comes back with the right tool, and not with the wrong one. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theNewMachinesDropThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The bellows is wooden, so an axe is its correct tool; the other two are metal and glass.
        checkDrops(helper, level, new BlockPos(1, 1, 1), TechnomBlocks.FLUX_LAMP.get(), Items.DIAMOND_PICKAXE);
        checkDrops(helper, level, new BlockPos(2, 1, 2), TechnomBlocks.BIOME_MORPHER.get(), Items.DIAMOND_PICKAXE);
        checkDrops(helper, level, new BlockPos(3, 1, 3), TechnomBlocks.ELECTRIC_BELLOWS.get(), Items.DIAMOND_AXE);
        helper.succeed();
    }

    private static void checkDrops(GameTestHelper helper, ServerLevel level, BlockPos relative, Block block,
            net.minecraft.world.item.Item tool) {
        BlockPos pos = helper.absolutePos(relative);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        BlockState state = level.getBlockState(pos);
        S2StorageGameTests.check(helper, state.is(block), block + " was not placed");
        S2StorageGameTests.check(helper, mineable(state), block + " is in no mineable tag");
        S2StorageGameTests.check(helper, new ItemStack(tool).isCorrectToolForDrops(state),
                tool + " is not accepted as the correct tool for " + block);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), null,
                new ItemStack(tool));
        S2StorageGameTests.check(helper, drops.size() == 1 && drops.get(0).is(block.asItem()),
                block + " dropped " + drops.size() + " stacks instead of itself");
    }

    /** Reachability: the three machines can be crafted and researched, not only placed in creative. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theNewRecipesAndResearchEntriesExist(GameTestHelper helper) {
        String[] recipes = {"arcane/biome_morpher", "arcane/electric_bellows", "infusion/flux_lamp"};
        for (String path : recipes) {
            S2StorageGameTests.check(helper,
                    helper.getLevel().getRecipeManager()
                            .byKey(new ResourceLocation(Technomancy.MOD_ID, path)).isPresent(),
                    "no recipe registered: " + path);
        }
        for (String key : new String[] {"technom:FLUXLAMP", "technom:ELECTRICBELLOWS", "technom:BIOMEMORPHER"}) {
            S2StorageGameTests.check(helper, ResearchCatalog.get(ResearchKey.parse(key)) != null,
                    key + " is not in the TC4R research catalog");
        }
        helper.succeed();
    }

    /**
     * Every block of the mod either deliberately has no loot table or has one that actually exists.
     *
     * <p>Forge gives a block that never called {@code noLootTable()} the table id
     * {@code technom:blocks/<id>}, and a missing file resolves to {@code LootTable.EMPTY} rather
     * than falling back to the block itself - so a block with no table drops nothing at all. The
     * tree had twenty-four of them, including every crystal, catalyst and Existence machine, and no
     * server-side test could see it because a missing table is not an error.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyBlockEitherHasALootTableOrSaysItDoesNot(GameTestHelper helper) {
        int checked = 0;
        int exempt = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!Technomancy.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            ResourceLocation table = block.getLootTable();
            if (table.equals(BuiltInLootTables.EMPTY)) {
                exempt++;
                continue;
            }
            checked++;
            S2StorageGameTests.check(helper,
                    helper.getLevel().getServer().getLootData().getLootTable(table) != LootTable.EMPTY,
                    "technom:" + id.getPath() + " points at loot table " + table
                            + ", which does not exist, so breaking it drops nothing");
        }
        S2StorageGameTests.check(helper, checked > 0, "no technom block has a loot table at all");
        Technomancy.LOGGER.info("GameTest S4: {} loot tables present, {} blocks opted out",
                checked, exempt);
        helper.succeed();
    }

    /**
     * A block entity that keeps a {@link MachineEnergy} has to hand it to neighbours, or nothing
     * can ever charge it.
     *
     * <p>Forge's {@code BlockEntity} extends {@code CapabilityProvider}, whose {@code getCapability}
     * answers {@code LazyOptional.empty()} unless a dispatcher was gathered from a registered
     * provider field - and {@code MachineEnergy} is not one. The biome morpher and the electric
     * bellows were the only two machines of twelve that never overrode it, so both were unpowerable
     * and never ran; their drops test passed regardless, which is why nothing caught it.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyEnergyMachineHandsItsBufferToNeighbours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!Technomancy.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            if (!(level.getBlockEntity(pos) instanceof EnergyHolder)) {
                continue;
            }
            checked++;
            boolean exposed = false;
            for (Direction side : Direction.values()) {
                exposed |= level.getBlockEntity(pos)
                        .getCapability(ForgeCapabilities.ENERGY, side).isPresent();
            }
            exposed |= level.getBlockEntity(pos)
                    .getCapability(ForgeCapabilities.ENERGY, null).isPresent();
            S2StorageGameTests.check(helper, exposed,
                    "technom:" + id.getPath() + " holds a MachineEnergy but exposes no energy"
                            + " capability, so no neighbour can ever charge it");
        }
        S2StorageGameTests.check(helper, checked > 0, "no technom block entity holds energy at all");
        Technomancy.LOGGER.info("GameTest S4: {} energy machines expose their buffer", checked);
        helper.succeed();
    }
}
