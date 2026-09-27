package theflogat.technomancy.compat.gtceu;

/**
 * GregTech's voltage tiers as plain data. Machine definitions, tooltips and tests need the
 * rated voltages, so this table deliberately links no {@code com.gregtechceu} type and stays
 * usable when GTCEu is absent; only the adapter classes beside it link GT.
 *
 * <p>The names and voltages mirror {@code GTValues.VN} and {@code GTValues.V}. Nothing here
 * can be verified at compile time because the GT API is {@code compileOnly}, so
 * {@code GtceuEnergyGameTests} compares the whole table against the installed GTCEu and fails
 * if the two ever drift apart.</p>
 *
 * <p>Always derive a machine's EU limits from {@link #voltage()} rather than from the tier
 * index: the EU protocol rejects an over-voltage packet outright, so a wrong rating does not
 * throttle a machine, it disconnects it.</p>
 */
public enum EuTier {
    ULV(8),
    LV(32),
    MV(128),
    HV(512),
    EV(2048),
    IV(8192),
    LuV(32_768),
    ZPM(131_072),
    UV(524_288),
    UHV(2_097_152),
    UEV(8_388_608),
    UIV(33_554_432),
    UXV(134_217_728),
    OpV(536_870_912),
    MAX(2_147_483_648L);

    private static final EuTier[] TIERS = values();

    private final long voltage;

    EuTier(long voltage) {
        this.voltage = voltage;
    }

    /** Size of one ampere packet, in EU. */
    public long voltage() {
        return voltage;
    }

    /** Tier index, equal to GT's {@code GTValues.ULV} … {@code GTValues.MAX}. */
    public int index() {
        return ordinal();
    }

    /** Number of tiers, equal to GT's {@code GTValues.TIER_COUNT}. */
    public static int count() {
        return TIERS.length;
    }

    public static EuTier byIndex(int index) {
        if (index < 0 || index >= TIERS.length) {
            throw new IllegalArgumentException("no EU tier with index " + index);
        }
        return TIERS[index];
    }

    /**
     * Lowest tier that can carry {@code voltage} without over-volting a receiver rated for that
     * tier, clamped to {@link #ULV} below 8 EU and to {@link #MAX} above the highest rating.
     * This rounds up, matching GT's {@code GTUtil.getTierByVoltage}.
     */
    public static EuTier forVoltage(long voltage) {
        for (EuTier tier : TIERS) {
            if (voltage <= tier.voltage) {
                return tier;
            }
        }
        return MAX;
    }
}
