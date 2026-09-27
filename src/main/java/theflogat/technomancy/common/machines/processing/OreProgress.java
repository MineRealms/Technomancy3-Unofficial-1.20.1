package theflogat.technomancy.common.machines.processing;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * How many passes each processing module has already made on one purified ore.
 *
 * <p>The purity stage itself is <em>not</em> here: it is the item's identity (the original used
 * the damage value 0..5 of {@code ItemProcessedOre}, {@code MAXSTAGE = 6}), so a forged NBT tag
 * cannot disagree with the item about how pure it is. Ore → stage 0 with one pass; every further
 * pass adds one stage and one pass for that module ({@code TileProcessorBase.getOutput}, :67-77).
 * Each module allows two passes ({@code isProcessable}: {@code < 2}, :91) and the stage is capped,
 * which the original never checked — with the three shipped modules the per-module limit happened
 * to stop it at 5, but a fourth module would have walked the damage value past the last smelting
 * recipe.</p>
 *
 * <p>Immutable, and normalised so equal progress is always an equal value: entries are sorted and
 * non-positive counts are dropped, which is what lets identically processed ores stack.</p>
 */
public final class OreProgress {

    /** Six stages, 0..5, each smelting to one more ingot ({@code Ore.ingotsPerStage}). */
    public static final int STAGES = 6;
    public static final int MAX_STAGE = STAGES - 1;

    private static final String TAG_PASSES = "Passes";

    /** An ore that has never been processed. */
    public static final OreProgress NONE = new OreProgress(new TreeMap<>());

    private final Map<String, Integer> passes;

    private OreProgress(TreeMap<String, Integer> passes) {
        this.passes = Collections.unmodifiableMap(passes);
    }

    public boolean isEmpty() {
        return passes.isEmpty();
    }

    public int passes(ProcessingModule module) {
        return passes.getOrDefault(module.id(), 0);
    }

    /** All recorded passes, ordered by module id. */
    public Map<String, Integer> allPasses() {
        return passes;
    }

    /**
     * Whether {@code module} may process an ore that is at {@code stage} now.
     *
     * @param stage current purity stage, or {@code -1} for a raw ore that has none yet
     */
    public boolean canProcess(ProcessingModule module, int stage) {
        return stage < MAX_STAGE && passes(module) < module.maxPasses();
    }

    /**
     * The progress after one more pass through {@code module}.
     *
     * @throws IllegalStateException if the module has already used all its passes
     */
    public OreProgress next(ProcessingModule module) {
        if (passes(module) >= module.maxPasses()) {
            throw new IllegalStateException("module " + module.id() + " has no passes left: " + this);
        }
        TreeMap<String, Integer> updated = new TreeMap<>(passes);
        updated.merge(module.id(), 1, Integer::sum);
        return new OreProgress(updated);
    }

    /** Writes the record, or nothing at all when there is none, so clean stacks stay clean. */
    public void save(CompoundTag tag) {
        if (passes.isEmpty()) {
            tag.remove(TAG_PASSES);
            return;
        }
        CompoundTag saved = new CompoundTag();
        passes.forEach(saved::putInt);
        tag.put(TAG_PASSES, saved);
    }

    /** Reads a record, dropping non-positive counts and clamping absurd ones. */
    public static OreProgress load(CompoundTag tag) {
        TreeMap<String, Integer> passes = new TreeMap<>();
        CompoundTag saved = tag.getCompound(TAG_PASSES);
        for (String key : saved.getAllKeys()) {
            if (saved.contains(key, Tag.TAG_INT)) {
                int count = saved.getInt(key);
                if (count > 0) {
                    passes.put(key, Math.min(count, STAGES));
                }
            }
        }
        return passes.isEmpty() ? NONE : new OreProgress(passes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OreProgress that && passes.equals(that.passes);
    }

    @Override
    public int hashCode() {
        return passes.hashCode();
    }

    @Override
    public String toString() {
        return "OreProgress" + passes;
    }
}
