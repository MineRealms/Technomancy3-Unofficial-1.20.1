package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.research.ResearchCatalog;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
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
}
