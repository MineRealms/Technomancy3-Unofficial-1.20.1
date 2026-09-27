package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.machines.ProcessorBlock;
import theflogat.technomancy.common.items.PureOreItem;
import theflogat.technomancy.common.machines.processing.OreProgress;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.machines.processing.ProcessorCycle;
import theflogat.technomancy.common.machines.processing.PureOreMaterial;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomPureOres;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.ProcessorBlockEntity;
import theflogat.technomancy.common.tiles.machines.TcProcessorBlockEntity;

/**
 * S2 processing: the Ignis Incinerator in a real world. Written for the final batch run.
 *
 * <p>The two legacy defects this group must not reproduce each have a test:
 * {@link #aFullOutputSlotCostsNoEssentia} and {@link #everyFaceReachesTheRightSlot}.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S2ProcessingGameTests {

    private static final String BATCH = "technom_s2_processing";
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);

    private S2ProcessingGameTests() {}

    /** A raw ore is accepted, a twice-passed one is not, and ignis goes at exactly 2 per tick. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void ignisIsSpentPerWorkingTickAndNothingIsFree(GameTestHelper helper) {
        TcProcessorBlockEntity processor = place(helper);
        fill(processor, 64);
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 4));
        helper.runAfterDelay(40, () -> {
            // 64 ignis at cost 2 buys 32 working ticks and not one more.
            helper.assertTrue(processor.fuelAmount() == 0,
                    "ignis left after it should have run dry: " + processor.fuelAmount());
            helper.assertTrue(processor.progress() == 32,
                    "progress is " + processor.progress() + ", so a tick was worked without being paid for");
            helper.assertTrue(processor.items().getStackInSlot(ProcessorBlockEntity.SLOT_OUTPUT).isEmpty(),
                    "an unfinished job produced an item");
            helper.assertTrue(processor.items().getStackInSlot(ProcessorBlockEntity.SLOT_INPUT).getCount() == 4,
                    "the input was consumed before the job finished");
            Technomancy.LOGGER.info("GameTest processor: 64 ignis bought exactly {} of {} ticks",
                    processor.progress(), ProcessorCycle.TICKS);
            helper.succeed();
        });
    }

    /**
     * The defect this block is known for: the original started a cycle and paid fuel for all 60
     * ticks before it ever looked at the output slot.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void aFullOutputSlotCostsNoEssentia(GameTestHelper helper) {
        TcProcessorBlockEntity processor = place(helper);
        fill(processor, 64);
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 4));
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT, 64));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(processor.fuelAmount() == 64,
                    "a blocked processor burned " + (64 - processor.fuelAmount()) + " ignis");
            helper.assertTrue(processor.progress() == 0, "a blocked processor made progress");
            helper.assertFalse(helper.getBlockState(MACHINE).getValue(ProcessorBlock.LIT),
                    "a blocked processor is lit");
            helper.succeed();
        });
    }

    /** One raw ore becomes one purified ore that remembers which module made it. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void aFinishedJobYieldsOnePurifiedOre(GameTestHelper helper) {
        TcProcessorBlockEntity processor = place(helper);
        fill(processor, 64);
        // A creative jar below tops the buffer up by one unit a tick, which is what makes the
        // 120-ignis job affordable inside one test.
        ServerLevel level = helper.getLevel();
        BlockPos jarPos = helper.absolutePos(MACHINE.below());
        level.setBlockAndUpdate(jarPos, TechnomBlocks.CREATIVE_JAR.get().defaultBlockState());
        if (level.getBlockEntity(jarPos) instanceof CreativeJarBlockEntity jar) {
            helper.assertTrue(jar.setAspect(TcProcessorBlockEntity.IGNIS), "could not set the jar's aspect");
        } else {
            helper.fail("no creative jar below the processor");
        }
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 1));

        helper.runAfterDelay(30, () -> helper.assertTrue(helper.getBlockState(MACHINE).getValue(ProcessorBlock.LIT),
                "a working processor is not lit"));
        helper.runAfterDelay(90, () -> {
            ItemStack output = processor.items().getStackInSlot(ProcessorBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.getCount() == 1, "the output holds " + output.getCount() + " items");
            helper.assertTrue(output.getItem() == TechnomPureOres.item(PureOreMaterial.IRON, 0),
                    "the output is " + output.getItem() + " instead of stage 0 purified iron");
            helper.assertTrue(PureOreItem.progressOf(output).passes(ProcessingModule.THAUMCRAFT) == 1,
                    "the result does not record the pass that made it");
            helper.assertTrue(processor.items().getStackInSlot(ProcessorBlockEntity.SLOT_INPUT).isEmpty(),
                    "the input was not consumed");
            Technomancy.LOGGER.info("GameTest processor: raw iron -> {} with {} ignis left",
                    output, processor.fuelAmount());
            helper.succeed();
        });
    }

    /** The second pass costs more, and a third is refused outright. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void reprocessingCostsMoreAndStopsAfterTwoPasses(GameTestHelper helper) {
        TcProcessorBlockEntity processor = place(helper);
        fill(processor, 64);
        ItemStack once = purified(0, 1);
        ItemStack twice = purified(1, 2);
        helper.assertTrue(processor.items().isItemValid(ProcessorBlockEntity.SLOT_INPUT, once),
                "a once-processed ore was refused");
        helper.assertFalse(processor.items().isItemValid(ProcessorBlockEntity.SLOT_INPUT, twice),
                "a twice-processed ore was accepted for a third pass");
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_INPUT, once);
        helper.runAfterDelay(20, () -> {
            // Stage 1 with two passes costs 5 a tick, so 64 ignis buys 12 ticks and leaves 4.
            helper.assertTrue(processor.progress() == 12,
                    "progress is " + processor.progress() + " instead of the 12 ticks 64 ignis pays for");
            helper.assertTrue(processor.fuelAmount() == 4,
                    "ignis left is " + processor.fuelAmount() + " instead of 4");
            helper.succeed();
        });
    }

    /**
     * Hoppers and pipes must reach both slots, with the input insert-only and the output
     * extract-only. The 1.12 processors overrode the slot list with an empty array, so nothing
     * could reach them at all.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyFaceReachesTheRightSlot(GameTestHelper helper) {
        TcProcessorBlockEntity processor = place(helper);
        processor.items().setStackInSlot(ProcessorBlockEntity.SLOT_OUTPUT, purified(0, 1));
        for (Direction face : Direction.values()) {
            IItemHandler view = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            helper.assertTrue(view != null, face + ": no item handler at all");
            helper.assertTrue(view.getSlots() == ProcessorBlockEntity.SLOTS, face + ": wrong slot count");

            ItemStack ore = new ItemStack(Items.RAW_IRON, 4);
            helper.assertTrue(view.insertItem(ProcessorBlockEntity.SLOT_INPUT, ore, true).isEmpty(),
                    face + ": the input slot refused an ore");
            helper.assertTrue(view.isItemValid(ProcessorBlockEntity.SLOT_INPUT, ore),
                    face + ": the input slot reports the ore as invalid");
            // Refused even though it is exactly what the slot holds and would stack there, so the
            // refusal is the rule and not an item mismatch.
            helper.assertTrue(view.insertItem(ProcessorBlockEntity.SLOT_OUTPUT, purified(0, 1), true)
                            .getCount() == 1, face + ": the output slot accepted an insert");
            helper.assertFalse(view.isItemValid(ProcessorBlockEntity.SLOT_OUTPUT, ore),
                    face + ": the output slot reports inserts as valid");
            helper.assertTrue(view.extractItem(ProcessorBlockEntity.SLOT_INPUT, 64, true).isEmpty(),
                    face + ": the input slot could be emptied by a pipe");
            helper.assertTrue(view.extractItem(ProcessorBlockEntity.SLOT_OUTPUT, 64, true).getCount() == 1,
                    face + ": the output slot could not be emptied");
            // Essentia comes in on every face and never leaves.
            helper.assertTrue(processor.canInputFrom(face) && processor.isConnectable(face),
                    face + ": essentia cannot get in");
            helper.assertFalse(processor.canOutputTo(face), face + ": essentia can get out");
            helper.assertTrue(processor.essentiaAmount(face) == 0
                            && processor.availableEssentia(TcProcessorBlockEntity.IGNIS, face) == 0,
                    face + ": advertises fuel a tube could try to pull");
            helper.assertTrue(processor.takeEssentia(TcProcessorBlockEntity.IGNIS, 1, face,
                    EssentiaTransferMode.EXECUTE) == 0, face + ": handed fuel back out");
        }
        IItemHandler internal = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        helper.assertTrue(internal != null, "no side-less item handler");
        helper.assertTrue(internal.insertItem(ProcessorBlockEntity.SLOT_OUTPUT, purified(0, 1), true)
                        .isEmpty(),
                "the side-less view is the machine's own and must be able to fill the output slot");
        helper.succeed();
    }

    // ---- helpers ----

    private static TcProcessorBlockEntity place(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.getLevel().setBlockAndUpdate(pos, TechnomBlocks.PROCESSOR_TC.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof TcProcessorBlockEntity processor)) {
            throw new IllegalStateException("no processor block entity at " + pos);
        }
        return processor;
    }

    private static void fill(TcProcessorBlockEntity processor, int ignis) {
        int added = processor.addEssentia(TcProcessorBlockEntity.IGNIS, ignis, Direction.NORTH,
                EssentiaTransferMode.EXECUTE);
        if (added != ignis) {
            throw new IllegalStateException("the processor took " + added + " of " + ignis + " ignis");
        }
    }

    /** A purified iron stack at {@code stage} that has been through the TC module {@code passes} times. */
    private static ItemStack purified(int stage, int passes) {
        ItemStack stack = new ItemStack(TechnomPureOres.item(PureOreMaterial.IRON, stage));
        OreProgress progress = OreProgress.NONE;
        for (int pass = 0; pass < passes; pass++) {
            progress = progress.next(ProcessingModule.THAUMCRAFT);
        }
        CompoundTag tag = new CompoundTag();
        progress.save(tag);
        if (!tag.isEmpty()) {
            stack.setTag(tag);
        }
        return stack;
    }
}
