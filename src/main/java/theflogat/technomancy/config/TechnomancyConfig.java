package theflogat.technomancy.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common (both sides, not per-world) settings. */
public final class TechnomancyConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.LongValue Q_PER_EU;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("energy");
        Q_PER_EU = builder
                .comment("FE per GT EU used by Technomancy machines. Read once at startup; keep it equal to",
                        "GTCEu's compat.energy ratios so FE/EU conversion loops cannot create energy.")
                .defineInRange("fePerEu", 4L, 1L, 1024L);
        builder.pop();
        SPEC = builder.build();
    }

    private TechnomancyConfig() {
    }
}
