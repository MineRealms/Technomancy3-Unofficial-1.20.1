package theflogat.technomancy.common.energy;

import java.util.Objects;

/**
 * The single authoritative energy balance of one machine, in the internal unit Q.
 *
 * <p>FE and GT EU are only protocol views over this ledger; they never hold a balance of
 * their own. External transfers go through the budgeted entry points ({@link #receive},
 * {@link #extract}, {@link #acceptPackets}, reservations), which share one input budget,
 * one output budget and the EU ampere counters per game tick. Machine-internal generation
 * and consumption ({@link #generate}, {@link #tryConsume}) bypass the transfer budgets but
 * never the capacity.</p>
 *
 * <p>Simulation never mutates anything, including the tick counters. The class is not
 * thread-safe; it is used on the logical server thread only.</p>
 */
public final class EnergyLedger {

    private EnergyLimits limits;
    private long stored;
    /** Q taken out of {@link #stored} by open output reservations; still counts as occupied capacity. */
    private long reserved;

    private long budgetTick = Long.MIN_VALUE;
    private long receivedThisTick;
    private long extractedThisTick;
    private long inputAmpsThisTick;
    private long outputAmpsThisTick;

    private Runnable listener = () -> {};

    public EnergyLedger(EnergyLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public EnergyLimits limits() {
        return limits;
    }

    /**
     * Replaces the limits (e.g. after an upgrade change). The balance is kept even when it
     * exceeds a smaller new capacity; no energy is destroyed, the store just stops accepting.
     */
    public void setLimits(EnergyLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public void setListener(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return limits.capacity();
    }

    /** Free capacity for new energy; reserved energy still occupies its place. */
    public long space() {
        return Math.max(0, limits.capacity() - stored - reserved);
    }

    // ---- budgeted external transfers ----

    /** FE-style input of arbitrary whole Q. Returns the amount accepted. */
    public long receive(long maxQ, long gameTime, boolean simulate) {
        if (maxQ <= 0) {
            return 0;
        }
        long accepted = min(maxQ, limits.maxReceivePerTick() - received(gameTime), space());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            roll(gameTime);
            receivedThisTick += accepted;
            stored += accepted;
            listener.run();
        }
        return accepted;
    }

    /** FE-style output of arbitrary whole Q. Returns the amount removed. */
    public long extract(long maxQ, long gameTime, boolean simulate) {
        if (maxQ <= 0) {
            return 0;
        }
        long taken = min(maxQ, limits.maxExtractPerTick() - extracted(gameTime), stored);
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            roll(gameTime);
            extractedThisTick += taken;
            stored -= taken;
            listener.run();
        }
        return taken;
    }

    /**
     * EU network input. Accepts only whole packets of {@code voltage × qPerEu} Q and returns the
     * number of accepted amperes, never more than requested. Packets above the rated input
     * voltage are rejected entirely (returns 0); nothing is truncated.
     */
    public long acceptPackets(long voltage, long amperage, long qPerEu, long gameTime, boolean simulate) {
        if (voltage <= 0 || amperage <= 0 || qPerEu <= 0) {
            return 0;
        }
        if (!limits.acceptsEu() || voltage > limits.euInputVoltage()) {
            return 0;
        }
        long qPerPacket = multiplyOrZero(voltage, qPerEu);
        if (qPerPacket <= 0) {
            return 0;
        }
        long amps = min(amperage,
                limits.euInputAmps() - inputAmps(gameTime),
                space() / qPerPacket,
                Math.max(0, limits.maxReceivePerTick() - received(gameTime)) / qPerPacket);
        if (amps <= 0) {
            return 0;
        }
        if (!simulate) {
            long q = amps * qPerPacket; // bounded by space(), cannot overflow
            roll(gameTime);
            inputAmpsThisTick += amps;
            receivedThisTick += q;
            stored += q;
            listener.run();
        }
        return amps;
    }

    // ---- machine-internal operations (no transfer budget) ----

    /** Adds produced energy up to the free capacity. Returns the amount actually stored. */
    public long generate(long q) {
        if (q <= 0) {
            return 0;
        }
        long added = Math.min(q, space());
        if (added > 0) {
            stored += added;
            listener.run();
        }
        return added;
    }

    /** All-or-nothing internal consumption. */
    public boolean tryConsume(long q) {
        if (q < 0) {
            throw new IllegalArgumentException("negative consumption: " + q);
        }
        if (q == 0) {
            return true;
        }
        if (stored < q) {
            return false;
        }
        stored -= q;
        listener.run();
        return true;
    }

    // ---- active output with reservation ----

    /**
     * Moves up to {@code maxQ} out of the balance and the output budget before a neighbour is
     * called, so a re-entrant call from that neighbour cannot spend the same energy twice.
     * Always settle the result with {@link Reservation#commit} (or close it to refund).
     */
    public Reservation reserveOutput(long maxQ, long gameTime) {
        long amount = Math.max(0, min(maxQ, limits.maxExtractPerTick() - extracted(gameTime), stored));
        return open(amount, 0, 0, gameTime);
    }

    /**
     * Reserves whole EU output packets at the rated output voltage, bounded by the output
     * ampere limit, the output budget and the balance.
     */
    public Reservation reservePackets(long qPerEu, long gameTime) {
        if (!limits.emitsEu() || qPerEu <= 0) {
            return open(0, 0, 0, gameTime);
        }
        long qPerPacket = multiplyOrZero(limits.euOutputVoltage(), qPerEu);
        if (qPerPacket <= 0) {
            return open(0, 0, 0, gameTime);
        }
        long amps = Math.max(0, min(limits.euOutputAmps() - outputAmps(gameTime),
                stored / qPerPacket,
                Math.max(0, limits.maxExtractPerTick() - extracted(gameTime)) / qPerPacket));
        return open(amps * qPerPacket, amps, qPerPacket, gameTime);
    }

    private Reservation open(long q, long amps, long qPerAmp, long gameTime) {
        if (q > 0) {
            roll(gameTime);
            extractedThisTick += q;
            outputAmpsThisTick += amps;
            stored -= q;
            reserved += q;
        }
        return new Reservation(q, amps, qPerAmp, gameTime);
    }

    /** An in-flight output. Unused energy returns to the ledger when it is settled. */
    public final class Reservation implements AutoCloseable {
        private final long reservedQ;
        private final long reservedAmps;
        private final long qPerAmp;
        private final long tick;
        private boolean settled;

        private Reservation(long reservedQ, long reservedAmps, long qPerAmp, long tick) {
            this.reservedQ = reservedQ;
            this.reservedAmps = reservedAmps;
            this.qPerAmp = qPerAmp;
            this.tick = tick;
            this.settled = reservedQ == 0;
        }

        public long amount() {
            return reservedQ;
        }

        public long amps() {
            return reservedAmps;
        }

        /** EU voltage-sized packet in Q, or 0 for a plain Q reservation. */
        public long qPerAmp() {
            return qPerAmp;
        }

        /** Keeps {@code usedQ} (clamped to the reservation) spent and refunds the rest. */
        public long commit(long usedQ) {
            if (settled) {
                return 0;
            }
            long used = Math.max(0, Math.min(usedQ, reservedQ));
            long usedAmps = qPerAmp > 0 ? used / qPerAmp : 0;
            if (qPerAmp > 0) {
                used = usedAmps * qPerAmp;
            }
            settle(used, usedAmps);
            return used;
        }

        /** Packet variant: keeps {@code usedAmps} whole packets (clamped) and refunds the rest. */
        public long commitAmps(long usedAmps) {
            if (settled) {
                return 0;
            }
            if (qPerAmp <= 0) {
                throw new IllegalStateException("not a packet reservation");
            }
            long amps = Math.max(0, Math.min(usedAmps, reservedAmps));
            settle(amps * qPerAmp, amps);
            return amps;
        }

        private void settle(long used, long usedAmps) {
            settled = true;
            long refund = reservedQ - used;
            reserved -= reservedQ;
            stored += refund;
            if (budgetTick == tick) {
                extractedThisTick -= refund;
                outputAmpsThisTick -= reservedAmps - usedAmps;
            }
            if (used > 0) {
                listener.run();
            }
        }

        /** Refunds everything that was not committed. */
        @Override
        public void close() {
            commit(0);
        }
    }

    // ---- persistence ----

    /** The balance to persist, including anything still reserved. */
    public long storedForSave() {
        return stored + reserved;
    }

    /**
     * Restores a saved balance, clamped to {@code [0, capacity]}.
     *
     * @return {@code true} if the saved value was out of range and had to be clamped
     */
    public boolean loadStored(long q) {
        long clamped = Math.max(0, Math.min(q, limits.capacity()));
        stored = clamped;
        reserved = 0;
        return clamped != q;
    }

    // ---- tick bookkeeping; reads never roll the counters ----

    private long received(long tick) {
        return tick == budgetTick ? receivedThisTick : 0;
    }

    private long extracted(long tick) {
        return tick == budgetTick ? extractedThisTick : 0;
    }

    private long inputAmps(long tick) {
        return tick == budgetTick ? inputAmpsThisTick : 0;
    }

    private long outputAmps(long tick) {
        return tick == budgetTick ? outputAmpsThisTick : 0;
    }

    private void roll(long tick) {
        if (tick != budgetTick) {
            budgetTick = tick;
            receivedThisTick = 0;
            extractedThisTick = 0;
            inputAmpsThisTick = 0;
            outputAmpsThisTick = 0;
        }
    }

    private static long min(long a, long b, long c) {
        return Math.min(a, Math.min(b, c));
    }

    private static long min(long a, long b, long c, long d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    /** {@code a × b}, or 0 when the product overflows (such a packet can never fit). */
    private static long multiplyOrZero(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high == 0 && low >= 0 ? low : 0;
    }
}
