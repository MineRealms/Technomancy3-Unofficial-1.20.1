package theflogat.technomancy.common.machines.decon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * The registry-free half of the deconstruction table: the primal total, the two-gate roll and
 * the break time. Flattening compound aspects needs the live aspect registry, so it is covered
 * in the in-game test instead.
 */
class DeconstructionTableTest {

    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId AQUA = AspectId.parse("aqua");

    private static int rewardCount(AspectAmounts primals, int samples) {
        int count = 0;
        for (long seed = 0; seed < samples; seed++) {
            if (DeconstructionTable.rollReward(primals, RandomSource.create(seed)).isPresent()) {
                count++;
            }
        }
        return count;
    }

    @Test
    void primalTotalSumsEveryEntry() {
        LinkedHashMap<AspectId, Integer> amounts = new LinkedHashMap<>();
        amounts.put(IGNIS, 30);
        amounts.put(AQUA, 12);
        assertEquals(42, DeconstructionTable.primalTotal(new AspectAmounts(amounts)));
        assertEquals(0, DeconstructionTable.primalTotal(AspectAmounts.EMPTY));
    }

    @Test
    void anEmptyObjectNeverRewards() {
        assertEquals(0, rewardCount(AspectAmounts.EMPTY, 2000));
    }

    @Test
    void aRewardIsAlwaysOneOfTheObjectsPrimals() {
        AspectAmounts primals = new AspectAmounts(Map.of(IGNIS, 60, AQUA, 60));
        int seen = 0;
        for (long seed = 0; seed < 4000; seed++) {
            var reward = DeconstructionTable.rollReward(primals, RandomSource.create(seed));
            if (reward.isPresent()) {
                assertTrue(IGNIS.equals(reward.get()) || AQUA.equals(reward.get()),
                        "rewarded " + reward.get() + ", which the object did not contain");
                seen++;
            }
        }
        assertTrue(seen > 0, "no reward in 4000 samples despite a total of 120");
    }

    /**
     * The first gate is {@code rand(80) < primalTotal}. With a total of 80 it always passes, so
     * the rate is the original one-in-eight; with a total of 40 it halves again.
     */
    @Test
    void thePrimalTotalDrivesTheFirstGate() {
        int full = rewardCount(new AspectAmounts(Map.of(IGNIS, 80)), 16000);
        int half = rewardCount(new AspectAmounts(Map.of(IGNIS, 40)), 16000);
        // 16000 samples: ~2000 at 1/8 and ~1000 at 1/16. Generous bands, fixed seeds, no flakes.
        assertTrue(full > 1800 && full < 2200, "1/8 rate produced " + full + " rewards");
        assertTrue(half > 850 && half < 1150, "1/16 rate produced " + half + " rewards");
        assertTrue(full > half, "the first gate did not depend on the primal total");
    }

    @Test
    void theRewardIsNotProducedWhenTheFirstGateFails() {
        // A total of one: nextInt(80) is below 1 only for one seed in 80, and the second gate
        // then cuts it by eight. Over 2000 samples the count is tiny but not zero.
        int count = rewardCount(new AspectAmounts(Map.of(IGNIS, 1)), 2000);
        assertTrue(count <= 8, "a near-worthless object rewarded " + count + " times in 2000");
    }

    @Test
    void thePotencyGemHalvesTheBreakTime() {
        assertEquals(80, DeconstructionTable.breakTicks(false));
        assertEquals(40, DeconstructionTable.breakTicks(true));
    }
}
