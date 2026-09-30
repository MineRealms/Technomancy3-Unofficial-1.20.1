package theflogat.technomancy.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common (both sides, not per-world) settings. */
public final class TechnomancyConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.LongValue Q_PER_EU;
    public static final ForgeConfigSpec.DoubleValue ESSENTIA_FUEL_SCALE;
    public static final ForgeConfigSpec.LongValue CONDENSER_COST;
    public static final ForgeConfigSpec.LongValue CONSUMER_COST;
    public static final ForgeConfigSpec.BooleanValue TREASURES;
    public static final ForgeConfigSpec.LongValue MANA_FABRICATOR_COST;
    public static final ForgeConfigSpec.LongValue BIOME_MORPHER_COST;

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
        CONSUMER_COST = builder
                .comment("Energy the eldritch consumer spends per object it destroys, in Q.",
                        "One charge is paid for each item entity, mob or block it consumes. The default is",
                        "the original 20000; the buffer holds fifty charges, also as in 1.7.10.")
                .defineInRange("eldritchConsumerCostQ", 20_000L, 1L, Long.MAX_VALUE);
        MANA_FABRICATOR_COST = builder
                .comment("Energy the Botania mana fabricator spends per 100 mana, in Q.",
                        "1.7.10 charged 1000000 per operation; the 1.12 fork cut it to 5000, which is a",
                        "rebalance by that fork's author and not upstream consensus, so the default stays",
                        "at the original. Set it to 5000 to play with the fork's cheaper rate.")
                .defineInRange("manaFabricatorCostQ", 1_000_000L, 1L, Long.MAX_VALUE);
        BIOME_MORPHER_COST = builder
                .comment("Energy the biome morpher spends per biome column it converts, in Q.",
                        "The original 20000 is what its 800000 Q buffer buys forty conversions with.")
                .defineInRange("biomeMorpherCostQ", 20_000L, 1L, Long.MAX_VALUE);
        builder.pop();

        builder.push("world");
        TREASURES = builder
                .comment("Let villagers carry the three treasures, which the Extraction ritual then takes.",
                        "Off by default, as in 1.7.10: the original gated this behind a second option,",
                        "treasureSafeguard, that shipped false, so its treasures flag ended up false too.",
                        "Turning this on makes one villager in fifty a carrier. Killing an unsealed",
                        "carrier destroys the treasure and sets off its revenge - a radius-30 explosion",
                        "for the fire gem - while killing one the sealing device has marked drops the",
                        "treasure instead. Both are a reason to keep this off on a public server.")
                .define("treasures", false);
        builder.pop();

        SPEC = builder.build();
    }

    private TechnomancyConfig() {
    }
}
