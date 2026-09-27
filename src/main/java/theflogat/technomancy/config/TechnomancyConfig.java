package theflogat.technomancy.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common (both sides, not per-world) settings. */
public final class TechnomancyConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.LongValue Q_PER_EU;
    public static final ForgeConfigSpec.DoubleValue ESSENTIA_FUEL_SCALE;
    public static final ForgeConfigSpec.LongValue CONDENSER_COST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("energy");
        Q_PER_EU = builder
                .comment("FE per GT EU used by Technomancy machines. Read once at startup; keep it equal to",
                        "GTCEu's compat.energy ratios so FE/EU conversion loops cannot create energy.")
                .defineInRange("fePerEu", 4L, 1L, 1024L);
        builder.pop();

        builder.push("balance");
        ESSENTIA_FUEL_SCALE = builder
                .comment("Multiplier on the energy one unit of essentia is worth in a dynamo.",
                        "A faithful 1:1 port of the 1.7.10 values is 1.0, but it is exploitable: one unit of",
                        "ignis or potentia is worth 64000 Q, and Thaumcraft's coal carries potentia 2 plus",
                        "ignis 2, so putting coal through a crucible yields four units worth 256000 Q against",
                        "roughly 64000 Q from simply burning it - a renewable fourfold gain. The default of",
                        "0.25 makes the two routes break even. Dynamo output rate is unaffected; only how",
                        "long a unit burns changes.")
                .defineInRange("essentiaFuelScale", 0.25D, 0.0D, 16.0D);
        CONDENSER_COST = builder
                .comment("Energy the condenser spends per unit of essentia it creates, in Q.",
                        "Hard constraint: this must stay strictly greater than what a dynamo yields from",
                        "burning one unit of the aspect the condenser produces, or the pair is a perpetual",
                        "motion machine. The original 1000000 left a 15.6x margin at 1:1 fuel value; 200000",
                        "keeps a 12.5x margin at the default fuel scale without the multi-hour wait.")
                .defineInRange("condenserCostQ", 200_000L, 1L, Long.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private TechnomancyConfig() {
    }
}
