package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.item.ScribeTools;
import dev.tc4port.thaumcraft.block.entity.ResearchTableBlockEntity;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.PenItem;
import theflogat.technomancy.common.registry.TechnomItems;

/**
 * The pen against real Thaumcraft tables: it is ink for the research table, and clicking one of
 * two adjacent tables with it builds the table with the pen already in the scribing slot.
 * Written for the merged final run; not yet executed.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PenGameTests {

    private static final String BATCH = "technom_pen";

    private PenGameTests() {}

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void thePenIsAScribingToolWithThreeThousandUses(GameTestHelper helper) {
        ItemStack pen = new ItemStack(TechnomItems.PEN.get());
        helper.assertTrue(pen.getMaxDamage() == PenItem.INK_USES, "durability is " + pen.getMaxDamage());
        helper.assertTrue(ScribeTools.isUsable(pen), "a fresh pen is not usable as a scribing tool");
        ScribeTools.consume(pen);
        helper.assertTrue(pen.getDamageValue() == 1, "one use should cost one point of ink");
        pen.setDamageValue(PenItem.INK_USES);
        helper.assertTrue(!ScribeTools.isUsable(pen), "a dry pen must not supply ink");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void thePenFormsTheResearchTable(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 2);
        BlockPos right = left.east();
        helper.setBlock(left, TCBlocks.TABLE.get());
        helper.setBlock(right, TCBlocks.TABLE.get());

        Player player = helper.makeMockPlayer();
        ItemStack pen = new ItemStack(TechnomItems.PEN.get());
        pen.setDamageValue(7);
        player.setItemInHand(InteractionHand.MAIN_HAND, pen);
        helper.useBlock(left, player);

        BlockEntity table = helper.getBlockEntity(left);
        helper.assertTrue(table instanceof ResearchTableBlockEntity,
                "clicking two tables with a pen did not form a research table: " + table);
        ItemStack slot = ((ResearchTableBlockEntity) table).getItem(ResearchTableBlockEntity.SCRIBING_SLOT);
        helper.assertTrue(slot.is(TechnomItems.PEN.get()), "the scribing slot holds " + slot);
        helper.assertTrue(slot.getDamageValue() == 7, "the pen lost its remaining ink in the move");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the pen was not taken out of the player's hand");
        helper.succeed();
    }
}
