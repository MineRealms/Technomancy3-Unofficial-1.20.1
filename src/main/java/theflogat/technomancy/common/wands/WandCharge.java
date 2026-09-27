package theflogat.technomancy.common.wands;

import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Energy-to-Vis arithmetic for the energized and technoturge rods. World-free and testable.
 *
 * <p>Both 1.7.10 paths charged 10000 RF per whole Vis ({@code ElectricWandUpdate}:
 * {@code energy -= 10000} per {@code addVis(.., 1, ..)}; {@code ItemTechnoturgeScepter.receiveEnergy}
 * the same), and TC4's {@code addVis} amount was whole Vis = 100 centivis. So one centivis is
 * exactly {@value #Q_PER_CENTIVIS} Q, and charging in centivis instead of whole Vis leaves no
 * remainder to lose: the original banked up to 9999 RF in a stack tag and, when a single
 * {@code receiveEnergy} call crossed a 10000 boundary, discarded the part it did not convert.</p>
 */
public final class WandCharge {

    /** 10000 RF per whole Vis, 100 centivis per whole Vis. */
    public static final long Q_PER_CENTIVIS = 100;
    /**
     * Most centivis one channel gains per charging pass (every 20 ticks). The original refilled a
     * 20000 RF bank once per primal per tick, i.e. at most two Vis per primal per tick, so 40 Vis
     * per second; this keeps that ceiling.
     */
    public static final int MAX_CENTIVIS_PER_CHANNEL_PER_PASS = 4000;

    private WandCharge() {
    }

    /** Free room per channel in centivis, never negative. */
    public static Map<VisChannel, Integer> room(Map<VisChannel, Integer> current, Map<VisChannel, Integer> capacity) {
        EnumMap<VisChannel, Integer> room = new EnumMap<>(VisChannel.class);
        for (VisChannel channel : VisChannel.values()) {
            int free = capacity.getOrDefault(channel, 0) - current.getOrDefault(channel, 0);
            room.put(channel, Math.max(0, free));
        }
        return room;
    }

    /** Total room in Q, saturating. */
    public static long roomQ(Map<VisChannel, Integer> room) {
        long centivis = 0;
        for (int free : room.values()) {
            centivis += Math.max(0, free);
        }
        return saturatingMultiply(centivis, Q_PER_CENTIVIS);
    }

    /**
     * Splits what {@code bufferQ} can pay for across the channels with room, evenly, so a
     * partly paid pass fills all six primals together as the original's round robin did
     * ({@code ItemTechnoturgeScepter.receiveEnergy} added one Vis per primal in turn).
     *
     * @param perChannelLimit cap per channel for this pass, in centivis
     * @return centivis to insert per channel; the cost is their sum times {@link #Q_PER_CENTIVIS}
     */
    public static Map<VisChannel, Integer> plan(Map<VisChannel, Integer> room, long bufferQ, int perChannelLimit) {
        EnumMap<VisChannel, Integer> plan = new EnumMap<>(VisChannel.class);
        EnumMap<VisChannel, Integer> left = new EnumMap<>(VisChannel.class);
        for (VisChannel channel : VisChannel.values()) {
            left.put(channel, Math.max(0, Math.min(room.getOrDefault(channel, 0), perChannelLimit)));
        }
        long affordable = Math.max(0, bufferQ) / Q_PER_CENTIVIS;
        while (affordable > 0) {
            int open = 0;
            for (int free : left.values()) {
                if (free > 0) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            long share = Math.max(1, affordable / open);
            for (VisChannel channel : VisChannel.values()) {
                int free = left.get(channel);
                if (free <= 0 || affordable <= 0) {
                    continue;
                }
                int give = (int) Math.min(Math.min(share, free), affordable);
                plan.merge(channel, give, Integer::sum);
                left.put(channel, free - give);
                affordable -= give;
            }
        }
        return plan;
    }

    /** Sum of a centivis map. */
    public static long total(Map<VisChannel, Integer> centivis) {
        long sum = 0;
        for (int value : centivis.values()) {
            sum += value;
        }
        return sum;
    }

    static long saturatingMultiply(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return (high == 0 && low >= 0) ? low : Long.MAX_VALUE;
    }
}
