package theflogat.technomancy.common.machines.decon;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * The rules of the advanced deconstruction table ({@code TileAdvDeconTable}): how an object's
 * aspects are flattened to primals, and when one of those primals becomes a research point.
 *
 * <p>The 1.7.10 numbers are kept: an object is broken in 80 ticks (40 with the potency gem),
 * and the roll is {@code rand(80) < primalCount} followed by a one-in-eight chance, then a
 * uniform pick from the primals the object actually had. Flattening is the original
 * {@code reduceToPrimals}: a compound contributes its full amount to each of its two
 * components, recursively.</p>
 *
 * <p>This is pure logic with no world or player access, so every branch is unit-testable; the
 * block entity owns the timer, the owner and the pool grant.</p>
 */
public final class DeconstructionTable {

    /** {@code TileAdvDeconTable.breakSpeed}. */
    public static final int BREAK_TICKS = 80;
    /** {@code IUpgradable} boost: half the break time, not twice the yield. */
    public static final int BOOSTED_BREAK_TICKS = 40;
    /** {@code TileAdvDeconTable.updateEntity}: one point per 20 ticks. */
    public static final int AWARD_INTERVAL = 20;
    /**
     * {@code TileAdvDeconTable.updateEntity} refused to credit a primal at
     * {@code Short.MAX_VALUE}. The cap is kept; unlike the original, reaching it discards the
     * pending point instead of leaving it set, which used to jam the machine forever
     * ({@code aspect} also gates {@code canBreak}).
     */
    public static final int REWARD_CAP = Short.MAX_VALUE;

    private static final int ROLL_MODULUS = 80;
    private static final int RARE_DENOMINATOR = 8;

    private DeconstructionTable() {
    }

    /** Flattens a per-unit aspect map to its primal components, summing shared primals. */
    public static AspectAmounts reduceToPrimals(AspectAmounts aspects) {
        LinkedHashMap<AspectId, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<AspectId, Integer> entry : aspects.amounts().entrySet()) {
            decompose(entry.getKey(), entry.getValue(), out);
        }
        return out.isEmpty() ? AspectAmounts.EMPTY : new AspectAmounts(out);
    }

    private static void decompose(AspectId aspect, int amount, Map<AspectId, Integer> out) {
        if (amount <= 0) {
            return;
        }
        AspectDefinition definition = AspectApi.get(aspect);
        if (definition == null) {
            return;
        }
        if (definition.primal()) {
            out.merge(aspect, amount, Integer::sum);
            return;
        }
        for (AspectId component : definition.components()) {
            decompose(component, amount, out);
        }
    }

    /** The sum of every primal amount, i.e. {@code AspectList.visSize()} after the reduction. */
    public static int primalTotal(AspectAmounts primals) {
        long total = 0;
        for (int amount : primals.amounts().values()) {
            total += amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * The original roll, in the original order: the {@code rand(80) < primalCount} test is
     * evaluated first, so the rare one-in-eight draw only happens when it passed.
     */
    public static Optional<AspectId> rollReward(AspectAmounts primals, RandomSource random) {
        if (primals.amounts().isEmpty()) {
            return Optional.empty();
        }
        if (random.nextInt(ROLL_MODULUS) >= primalTotal(primals)) {
            return Optional.empty();
        }
        if (random.nextInt(RARE_DENOMINATOR) != 0) {
            return Optional.empty();
        }
        List<AspectId> present = new ArrayList<>(primals.amounts().keySet());
        return Optional.of(present.get(random.nextInt(present.size())));
    }

    public static int breakTicks(boolean boosted) {
        return boosted ? BOOSTED_BREAK_TICKS : BREAK_TICKS;
    }
}
