package theflogat.technomancy.common.machines;

import net.minecraft.nbt.CompoundTag;
import theflogat.technomancy.common.energy.EnergyLedger;

/**
 * The condenser's production accounting: energy goes in at a bounded rate, and every time
 * {@code cost} Q has gone in, exactly one unit of essentia comes out.
 *
 * <p>Deliberate deviation from 1.7.10, which gated on {@code energy >= cost} and then took the
 * whole cost in a single tick. Behaviourally the two agree on the exchange rate; this one
 * makes partial progress visible and lets the machine run off a supply smaller than one unit's
 * cost without a five-million-Q hoard. The rate limit is what makes it a "progress bar" rather
 * than the same lump spend with extra steps.</p>
 *
 * <p>Conservation is structural, not checked after the fact: a tick never draws more than
 * {@code cost - progress}, so {@code progress} can never exceed {@code cost}, one unit costs
 * exactly {@code cost} Q with no remainder to round away, and stopping mid-cycle keeps the
 * partial progress instead of discarding it. The class owns no world state and is not
 * thread-safe; it runs on the logical server thread.</p>
 */
public final class CondenserProduction {

    private static final String TAG_VERSION = "v";
    private static final String TAG_PROGRESS = "q";
    private static final int SCHEMA_VERSION = 1;

    private final long costQ;
    private final long maxRateQPerTick;
    /** Q already spent towards the unit being made; always in {@code [0, costQ)} between ticks. */
    private long progress;

    public CondenserProduction(long costQ, long maxRateQPerTick) {
        if (costQ <= 0) {
            throw new IllegalArgumentException("cost must be positive: " + costQ);
        }
        if (maxRateQPerTick <= 0) {
            throw new IllegalArgumentException("rate must be positive: " + maxRateQPerTick);
        }
        this.costQ = costQ;
        this.maxRateQPerTick = maxRateQPerTick;
    }

    public long costQ() {
        return costQ;
    }

    public long maxRateQPerTick() {
        return maxRateQPerTick;
    }

    public long progressQ() {
        return progress;
    }

    /** Fraction of the current unit that is paid for, in {@code [0, 1)}. */
    public float progressFraction() {
        return (float) progress / costQ;
    }

    /**
     * Runs one tick of conversion.
     *
     * <p>With no room for the result nothing at all happens: no energy is drawn and no progress
     * is made. The 1.7.10 dynamo had the mirror-image bug of drawing fuel it could not store
     * (defect A-14); a full condenser must likewise not burn energy it cannot turn into
     * anything.</p>
     *
     * @param ledger the machine's single energy balance; spent all-or-nothing
     * @param space  essentia units that still fit in the output buffer
     * @return units produced this tick, which is 0 or 1
     */
    public int tick(EnergyLedger ledger, int space) {
        if (space <= 0) {
            return 0;
        }
        long wanted = Math.min(maxRateQPerTick, costQ - progress);
        long affordable = Math.min(wanted, ledger.stored());
        // All-or-nothing: a refusal here leaves both the ledger and the progress untouched,
        // so no Q can be consumed without being credited to the unit being made.
        if (affordable > 0 && ledger.tryConsume(affordable)) {
            progress += affordable;
        }
        if (progress < costQ) {
            return 0;
        }
        progress -= costQ;
        return 1;
    }

    /** Total Q spent on units not yet finished; what a break or a save has to account for. */
    public long unfinishedQ() {
        return progress;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.putLong(TAG_PROGRESS, progress);
        return tag;
    }

    /**
     * Restores saved progress, clamped to {@code [0, costQ)}.
     *
     * @return {@code true} if the saved value was unusable and had to be discarded or clamped,
     *         i.e. the caller should log that the save was not restored verbatim
     */
    public boolean load(CompoundTag tag) {
        progress = 0;
        if (tag.isEmpty()) {
            return false;
        }
        if (tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
            return true;
        }
        long saved = tag.getLong(TAG_PROGRESS);
        // A cost lowered by config can strand progress above the new cost; clamping below it
        // keeps the invariant without handing out a free unit on the next tick.
        progress = Math.max(0, Math.min(saved, costQ - 1));
        return progress != saved;
    }
}
