package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.machines.decon.DeconstructionTable;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.machines.AdvDeconTableBlockEntity;

/**
 * In-world behaviour of the advanced deconstruction table: the live aspect registry flattens
 * compound aspects to primals, the loop eats an object over time, and automation reaches the
 * slot.
 *
 * <p>The pool grant itself needs a real player in the server's player list, which a GameTest
 * cannot supply, so the reward path is driven here only through its observable effect on the
 * loop: an ownerless table discards each rolled point and keeps eating.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeconTableGameTests {

    private static final String BATCH = "technom_s2_decon";
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);
    private static final AspectId POTENTIA = AspectId.parse("potentia");
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId ORDO = AspectId.parse("ordo");

    private DeconTableGameTests() {}

    /** {@code reduceToPrimals} against the live registry: potentia is ignis + ordo. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void compoundAspectsFlattenToTheirPrimals(GameTestHelper helper) {
        AspectAmounts primals = DeconstructionTable.reduceToPrimals(new AspectAmounts(Map.of(POTENTIA, 3)));
        helper.assertTrue(primals.amount(IGNIS) == 3 && primals.amount(ORDO) == 3,
                "potentia x3 flattened to " + primals.amounts() + " instead of ignis 3 + ordo 3");
        helper.assertTrue(DeconstructionTable.primalTotal(primals) == 6,
                "the primal total is " + DeconstructionTable.primalTotal(primals) + " instead of 6");
        helper.succeed();
    }

    /** An object with aspects is eaten over time even with no owner to credit. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 400)
    public static void theTableConsumesAnObjectOwnerlessly(GameTestHelper helper) {
        AdvDeconTableBlockEntity table = place(helper);
        helper.assertTrue(AdvDeconTableBlockEntity.decomposable(new ItemStack(Items.IRON_INGOT)),
                "an iron ingot reports no aspects, so the table would never start");
        helper.assertFalse(AdvDeconTableBlockEntity.decomposable(ItemStack.EMPTY),
                "an empty stack reports aspects");
        table.items().setStackInSlot(AdvDeconTableBlockEntity.SLOT, new ItemStack(Items.IRON_INGOT, 2));

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = level.getBlockState(pos);
        for (int tick = 0; tick < 200; tick++) {
            AdvDeconTableBlockEntity.serverTick(level, pos, state, table);
        }
        helper.assertTrue(table.items().getStackInSlot(AdvDeconTableBlockEntity.SLOT).isEmpty(),
                "the table still holds "
                        + table.items().getStackInSlot(AdvDeconTableBlockEntity.SLOT).getCount()
                        + " objects after 200 ticks");
        helper.succeed();
    }

    /** Hoppers and pipes reach the single slot through a real handler on every face. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void automationReachesTheSlot(GameTestHelper helper) {
        AdvDeconTableBlockEntity table = place(helper);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 4);
        for (Direction face : Direction.values()) {
            IItemHandler view = table.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            helper.assertTrue(view != null, face + ": no item handler at all");
            helper.assertTrue(view.getSlots() == AdvDeconTableBlockEntity.SLOTS, face + ": wrong slot count");
            helper.assertTrue(view.insertItem(AdvDeconTableBlockEntity.SLOT, iron, true).isEmpty(),
                    face + ": the slot refused an ingot it should accept");
            helper.assertTrue(view.isItemValid(AdvDeconTableBlockEntity.SLOT, iron),
                    face + ": the slot reports the ingot as invalid");
        }
        IItemHandler internal = table.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        helper.assertTrue(internal != null, "no side-less item handler");
        helper.assertTrue(internal.insertItem(AdvDeconTableBlockEntity.SLOT, iron, true).isEmpty(),
                "the side-less view refused an ingot");
        helper.succeed();
    }

    private static AdvDeconTableBlockEntity place(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.getLevel().setBlockAndUpdate(pos, TechnomBlocks.ADV_DECON_TABLE.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof AdvDeconTableBlockEntity table)) {
            throw new IllegalStateException("no deconstruction table at " + pos);
        }
        return table;
    }
}
