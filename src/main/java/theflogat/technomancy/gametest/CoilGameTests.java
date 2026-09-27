package theflogat.technomancy.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.coils.CoilBlock;
import theflogat.technomancy.common.coils.CoilLink;
import theflogat.technomancy.common.coils.CoilLinks;
import theflogat.technomancy.common.items.coils.CoilCouplerItem;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.coils.ItemCoilBlockEntity;

/**
 * The item coil and the coupler in a real world, against vanilla chests.
 *
 * <p>Layout: source chest at (1,1,1), target chest at (3,1,3), coil on top of the target facing
 * down at (3,2,3). Every check counts items on both sides, so a pass means nothing was
 * duplicated or destroyed.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoilGameTests {

    static final String BATCH = "technom_coils";

    private CoilGameTests() {
    }

    record ItemRig(GameTestHelper helper, BlockPos source, BlockPos target, BlockPos coil) {
        static ItemRig place(GameTestHelper helper) {
            BlockPos source = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos target = helper.absolutePos(new BlockPos(3, 1, 3));
            BlockPos coil = target.above();
            helper.getLevel().setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(coil, TechnomBlocks.ITEM_COIL.get().defaultBlockState()
                    .setValue(CoilBlock.FACING, Direction.DOWN));
            return new ItemRig(helper, source, target, coil);
        }

        ChestBlockEntity chest(BlockPos pos) {
            return (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        }

        ItemCoilBlockEntity coilEntity() {
            return (ItemCoilBlockEntity) helper.getLevel().getBlockEntity(coil);
        }

        int count(BlockPos pos, net.minecraft.world.item.Item item) {
            return chest(pos).countItem(item);
        }

        void link() {
            helper.assertTrue(coilEntity().links().add(coil, new CoilLink(source, Direction.UP)) == CoilLinks.Result.ADDED,
                    "the source chest could not be linked");
        }
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void itemCoilMovesEverythingFromALinkedChestAndConservesIt(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        rig.chest(rig.source()).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        rig.chest(rig.source()).setItem(5, new ItemStack(Items.IRON_INGOT, 64));
        rig.chest(rig.source()).setItem(9, new ItemStack(Items.COBBLESTONE, 30));
        rig.link();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(rig.count(rig.source(), Items.COBBLESTONE) == 0 && rig.count(rig.source(), Items.IRON_INGOT) == 0,
                    "source still holds items after 20 ticks");
            helper.assertTrue(rig.count(rig.target(), Items.COBBLESTONE) == 70,
                    "target holds " + rig.count(rig.target(), Items.COBBLESTONE) + " cobblestone, expected 70");
            helper.assertTrue(rig.count(rig.target(), Items.IRON_INGOT) == 64, "iron was not conserved");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void aFullTargetTakesNothingAndNothingIsLost(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        ChestBlockEntity target = rig.chest(rig.target());
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            target.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        target.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        rig.chest(rig.source()).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        rig.link();
        helper.runAfterDelay(20, () -> {
            // Only 4 fit on top of the 60 already there.
            helper.assertTrue(rig.count(rig.target(), Items.COBBLESTONE) == 64, "the partial stack was not topped up");
            helper.assertTrue(rig.count(rig.source(), Items.COBBLESTONE) == 6,
                    "source holds " + rig.count(rig.source(), Items.COBBLESTONE) + ", expected the 6 that did not fit");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(rig.coil()).inflate(3)).isEmpty(), "items were spilled");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void theFilterLimitsWhatMoves(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        rig.chest(rig.source()).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        rig.chest(rig.source()).setItem(1, new ItemStack(Items.IRON_INGOT, 16));
        rig.coilEntity().setFilter(new ItemStack(Items.IRON_INGOT));
        rig.link();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(rig.count(rig.target(), Items.IRON_INGOT) == 16, "the filtered item did not move");
            helper.assertTrue(rig.count(rig.source(), Items.COBBLESTONE) == 16, "an unfiltered item moved");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void aRedstoneSignalStopsTheDefaultCoil(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        rig.chest(rig.source()).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.getLevel().setBlockAndUpdate(rig.coil().east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        rig.link();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(rig.count(rig.source(), Items.COBBLESTONE) == 16, "a powered coil moved items");
            helper.getLevel().setBlockAndUpdate(rig.coil().east(), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(rig.count(rig.target(), Items.COBBLESTONE) == 16, "the coil did not resume");
                helper.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void aRemovedSourceIsUnlinked(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        rig.link();
        helper.getLevel().setBlockAndUpdate(rig.source(), Blocks.STONE.defaultBlockState());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(rig.coilEntity().links().isEmpty(), "a link to a block with no inventory survived");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void linksAndFilterSurviveSaveAndLoad(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        rig.link();
        rig.coilEntity().setFilter(new ItemStack(Items.IRON_INGOT));
        CompoundTag saved = rig.coilEntity().saveWithoutMetadata();
        ItemCoilBlockEntity restored = new ItemCoilBlockEntity(rig.coil(), helper.getLevel().getBlockState(rig.coil()));
        restored.load(saved);
        helper.assertTrue(restored.links().view().equals(rig.coilEntity().links().view()), "links changed on reload");
        helper.assertTrue(restored.filter().is(Items.IRON_INGOT), "filter lost on reload");
        helper.succeed();
    }

    /** The whole coupler workflow through the item's real entry point. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theCouplerLinksTogglesAndRefuses(GameTestHelper helper) {
        ItemRig rig = ItemRig.place(helper);
        BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 3));
        helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer();
        ItemStack coupler = new ItemStack(TechnomItems.COIL_COUPLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, coupler);

        click(helper, player, coupler, rig.coil());
        helper.assertTrue(CoilCouplerItem.boundCoil(coupler).isPresent(), "clicking the coil did not bind the coupler");
        click(helper, player, coupler, rig.source());
        helper.assertTrue(rig.coilEntity().links().contains(rig.source()), "clicking the chest did not link it");
        click(helper, player, coupler, stone);
        helper.assertTrue(rig.coilEntity().links().size() == 1, "a block with no inventory was linked");
        click(helper, player, coupler, rig.source());
        helper.assertTrue(rig.coilEntity().links().isEmpty(), "clicking a linked chest again did not unlink it");
        helper.succeed();
    }

    static InteractionResult click(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
