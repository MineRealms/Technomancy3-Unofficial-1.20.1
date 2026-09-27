package theflogat.technomancy.common.machines.processing;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Progress accounting of one processor: a job takes {@link #TICKS} paid ticks, each paid
 * all-or-nothing, and finishes on the tick the last one is paid.
 *
 * <p>Fixes the legacy defect that gives this class its shape: {@code TileProcessorBase}
 * (:22-35) started a cycle and paid fuel for all 60 ticks before it ever looked at the output
 * slot, then sat at {@code progress == 0} retrying, so a full output slot cost a whole cycle of
 * fuel for nothing. Here a job whose output does not fit is not advanced and not paid for at
 * all.</p>
 *
 * <p>Progress belongs to one job, identified by a key (the planned output). If the key changes
 * — a different input was put in — progress restarts; if the input is merely removed, progress
 * waits for the same job to come back rather than being thrown away, which is more lenient than
 * the original (it reset to 0 whenever the slot was empty). The class owns no world state and
 * runs on the logical server thread.</p>
 */
public final class ProcessorCycle {

    /** {@code TileProcessorBase.maxTime}. */
    public static final int TICKS = 60;

    private static final String TAG_PROGRESS = "progress";

    /** Pays for one tick of work. */
    @FunctionalInterface
    public interface Payer {
        /** Pays {@code cost} all-or-nothing. @return whether it was paid */
        boolean pay(int cost);
    }

    public enum Outcome {
        /** Nothing to do: no job. */
        IDLE,
        /** A job exists but its output does not fit; nothing was paid. */
        BLOCKED,
        /** The tick could not be paid for; nothing changed. */
        STARVED,
        /** One tick was paid. */
        WORKED,
        /** The last tick was paid; the caller must move the item now. */
        COMPLETED
    }

    private int progress;
    @Nullable
    private Object key;

    public int progress() {
        return progress;
    }

    /**
     * Runs one tick.
     *
     * @param job        key of the current job, or {@code null} when there is none
     * @param outputFits whether the output of one completed job fits right now
     * @param cost       price of one tick, at least 1
     */
    public Outcome tick(@Nullable Object job, boolean outputFits, int cost, Payer payer) {
        if (job == null) {
            return Outcome.IDLE;
        }
        if (!Objects.equals(job, key)) {
            key = job;
            progress = 0;
        }
        if (!outputFits) {
            return Outcome.BLOCKED;
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("cost must be positive: " + cost);
        }
        if (!payer.pay(cost)) {
            return Outcome.STARVED;
        }
        progress++;
        if (progress < TICKS) {
            return Outcome.WORKED;
        }
        progress = 0;
        return Outcome.COMPLETED;
    }

    /** Saves progress; the job key is recomputed from the inventory after loading. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_PROGRESS, progress);
        return tag;
    }

    /**
     * Restores progress. The key is unknown until the first tick, which would reset a restored
     * value; {@link #adopt} binds it first.
     */
    public void load(CompoundTag tag) {
        progress = Math.max(0, Math.min(TICKS - 1, tag.getInt(TAG_PROGRESS)));
        key = null;
    }

    /** Binds restored progress to the job the loaded inventory implies, without resetting it. */
    public void adopt(@Nullable Object job) {
        if (key == null) {
            key = job;
            if (job == null) {
                progress = 0;
            }
        }
    }
}
