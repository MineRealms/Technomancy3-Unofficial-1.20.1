package theflogat.technomancy.common.wands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WandChargeTest {

    private static Map<VisChannel, Integer> flat(int value) {
        EnumMap<VisChannel, Integer> map = new EnumMap<>(VisChannel.class);
        for (VisChannel channel : VisChannel.values()) {
            map.put(channel, value);
        }
        return map;
    }

    @Test
    void roomIsPerChannelAndNeverNegative() {
        Map<VisChannel, Integer> current = flat(2500);
        current.put(VisChannel.AER, 3000);
        Map<VisChannel, Integer> room = WandCharge.room(current, flat(2500));
        assertEquals(0, room.get(VisChannel.AER), "an over-full channel must not report negative room");
        assertEquals(0, room.get(VisChannel.IGNIS));
        assertEquals(0, WandCharge.roomQ(room));

        room = WandCharge.room(flat(0), flat(2500));
        assertEquals(2500, room.get(VisChannel.AER));
        // 6 primals x 2500 centivis x 100 Q: a full energized wand (25 whole vis) costs 150000 Q.
        assertEquals(6 * 2500 * WandCharge.Q_PER_CENTIVIS, WandCharge.roomQ(room));
    }

    @Test
    void planNeverExceedsRoomTheLimitOrTheBuffer() {
        Map<VisChannel, Integer> room = WandCharge.room(flat(0), flat(2500));
        for (long buffer : new long[] {0, 1, 99, 100, 101, 4999, 150_000, 1_000_000, Long.MAX_VALUE / 2}) {
            Map<VisChannel, Integer> plan = WandCharge.plan(room, buffer, 400);
            long cost = WandCharge.total(plan) * WandCharge.Q_PER_CENTIVIS;
            assertTrue(cost <= Math.max(0, buffer), "plan spent " + cost + " of " + buffer);
            plan.forEach((channel, amount) -> {
                assertTrue(amount > 0 && amount <= 400, channel + " got " + amount);
                assertTrue(amount <= room.get(channel), channel + " over room");
            });
        }
    }

    /** Whatever is left unspent has to be below one centivis, or the pass gave up early. */
    @Test
    void planSpendsEverythingItCan() {
        Map<VisChannel, Integer> room = WandCharge.room(flat(0), flat(2500));
        Map<VisChannel, Integer> plan = WandCharge.plan(room, 10_000, 2500);
        assertEquals(100, WandCharge.total(plan), "10000 Q must buy exactly 100 centivis");

        // 6 x 400 centivis is the whole pass allowance: 240000 Q. More than that stays buffered.
        plan = WandCharge.plan(room, 300_000, 400);
        assertEquals(6 * 400, WandCharge.total(plan));

        // One centivis is the granularity; 150 Q buys one and leaves 50 Q in the buffer.
        plan = WandCharge.plan(room, 150, 2500);
        assertEquals(1, WandCharge.total(plan));
        assertTrue(WandCharge.plan(room, 99, 2500).isEmpty(), "less than one centivis must buy nothing");
        assertTrue(WandCharge.plan(room, 10_000, 0).isEmpty(), "a zero per-pass limit charges nothing");
    }

    @Test
    void planFillsTheSixPrimalsTogether() {
        Map<VisChannel, Integer> room = WandCharge.room(flat(0), flat(2500));
        Map<VisChannel, Integer> plan = WandCharge.plan(room, 60 * WandCharge.Q_PER_CENTIVIS, 2500);
        assertEquals(6, plan.size(), "all six primals should advance: " + plan);
        plan.forEach((channel, amount) -> assertEquals(10, amount, channel.toString()));
    }

    /** A channel that is already full is skipped, and its share goes to the others. */
    @Test
    void planSkipsFullChannels() {
        Map<VisChannel, Integer> current = flat(0);
        current.put(VisChannel.AER, 2500);
        Map<VisChannel, Integer> room = WandCharge.room(current, flat(2500));
        Map<VisChannel, Integer> plan = WandCharge.plan(room, 50 * WandCharge.Q_PER_CENTIVIS, 2500);
        assertTrue(!plan.containsKey(VisChannel.AER), "a full channel must not be planned");
        assertEquals(50, WandCharge.total(plan));
        assertEquals(5, plan.size());
    }

    @Test
    void roomQSaturatesInsteadOfOverflowing() {
        Map<VisChannel, Integer> room = flat(Integer.MAX_VALUE);
        assertTrue(WandCharge.roomQ(room) > 0, "must not wrap negative");
    }
}
