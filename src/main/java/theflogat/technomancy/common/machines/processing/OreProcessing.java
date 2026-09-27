package theflogat.technomancy.common.machines.processing;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.common.items.PureOreItem;
import theflogat.technomancy.common.registry.TechnomPureOres;

/**
 * What one processor would make of the item in its input slot, and what that costs.
 *
 * <p>The original decided this in {@code TileProcessorBase.getOutput} (:67-77) by string
 * comparison against {@code OreDictionary} names ({@code Ore.oreName() == ...}, reference
 * equality on strings, {@code :119-123}) and by bumping the item damage. Here the input is
 * matched against a tag and the result is looked up by material and stage.</p>
 */
public final class OreProcessing {

    private OreProcessing() {
    }

    /**
     * A planned unit of work. Value-comparable, which is what lets {@link ProcessorCycle} notice
     * that the job in the slot has changed.
     *
     * @param result    item one completed job produces
     * @param progress  pass record the result carries
     * @param stage     purity stage of the result
     * @param tickCost  magic resource spent per working tick
     */
    public record Job(Item result, OreProgress progress, int stage, int tickCost) {

        /** One result item, carrying its pass record. */
        public ItemStack resultStack() {
            ItemStack stack = new ItemStack(result);
            CompoundTag tag = new CompoundTag();
            progress.save(tag);
            if (!tag.isEmpty()) {
                stack.setTag(tag);
            }
            return stack;
        }

        /** The whole job, i.e. what {@link ProcessorCycle#TICKS} paid ticks add up to. */
        public int totalCost() {
            return tickCost * ProcessorCycle.TICKS;
        }
    }

    /**
     * {@code TileTCProcessor.getFuel}: {@code max(1, multiplier + 2 * reprocess)}, where the
     * original passed the <em>result</em>'s damage value as the multiplier and its new pass count
     * for this module as {@code reprocess} ({@code TileTCProcessor.java:15-22} with
     * {@code TileProcessorBase.java:28}). Charged once per working tick, so a job costs 60 times
     * this: 120 for a raw ore, 300 for the second pass.
     */
    public static int tickCost(int resultStage, int resultPasses) {
        return Math.max(1, resultStage + 2 * resultPasses);
    }

    /** What {@code module} would make of {@code input}, or empty if it cannot process it. */
    public static Optional<Job> plan(ItemStack input, ProcessingModule module) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (input.getItem() instanceof PureOreItem pure) {
            OreProgress progress = PureOreItem.progressOf(input);
            if (!progress.canProcess(module, pure.stage())) {
                return Optional.empty();
            }
            int stage = pure.stage() + 1;
            OreProgress next = progress.next(module);
            return Optional.of(new Job(TechnomPureOres.item(pure.material(), stage), next, stage,
                    tickCost(stage, next.passes(module))));
        }
        for (PureOreMaterial material : PureOreMaterial.values()) {
            if (input.is(material.processable())) {
                OreProgress first = OreProgress.NONE.next(module);
                return Optional.of(new Job(TechnomPureOres.item(material, 0), first, 0,
                        tickCost(0, first.passes(module))));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether one result of {@code job} fits in {@code output} right now.
     *
     * <p>Checked <em>before</em> any resource is spent. The original ran a whole 60-tick cycle
     * paying fuel every tick and only then looked at the output slot, so a full output slot
     * burned a cycle's worth of essentia for nothing, every cycle
     * ({@code TileProcessorBase.java:22-40}).</p>
     */
    public static boolean fits(ItemStack output, Job job) {
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(output, job.resultStack())
                && output.getCount() < Math.min(output.getMaxStackSize(), 64);
    }
}
