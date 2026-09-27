package theflogat.technomancy.common.essentia.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.List;
import net.minecraft.util.ExtraCodecs;

/**
 * One row of the aspect fuel table: the aspects it covers, the value they are worth, and the
 * ordered conditions that can raise it.
 *
 * <p>A "fuel value" is not energy. It is the multiplier the dynamo turns into energy through
 * {@code fuelValue x 80 Q x essentiaFuelScale} per unit of essentia burned, exactly as the
 * 1.7.10 {@code getAspectFuel} return value did when it was a count of 80 RF/t ticks.</p>
 *
 * <p>{@link #conditions} is ordered and the <em>first</em> match wins. That is deliberate: it
 * gives an OR without a boolean combinator, which is all the original table ever needed (magic
 * and eldritch essentia were worth 300 "in the End <em>or</em> a magical forest").</p>
 */
public record EssentiaFuelEntry(List<AspectId> aspects, int value, List<ConditionalValue> conditions,
        int randomBonus) {

    public static final Codec<EssentiaFuelEntry> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(ExtraCodecs.nonEmptyList(AspectId.CODEC.listOf()).fieldOf("aspects")
                            .forGetter(EssentiaFuelEntry::aspects),
                    Codec.intRange(0, Integer.MAX_VALUE).fieldOf("value")
                            .forGetter(EssentiaFuelEntry::value),
                    FuelCodecs.strictOptionalField(ConditionalValue.CODEC.listOf(), "when", List.of())
                            .forGetter(EssentiaFuelEntry::conditions),
                    FuelCodecs.strictOptionalField(Codec.intRange(0, Integer.MAX_VALUE),
                                    "random_bonus", 0)
                            .forGetter(EssentiaFuelEntry::randomBonus))
            .apply(instance, EssentiaFuelEntry::new));

    public EssentiaFuelEntry {
        if (aspects.isEmpty()) {
            throw new IllegalArgumentException("a fuel entry must name at least one aspect");
        }
        aspects = List.copyOf(aspects);
        conditions = List.copyOf(conditions);
    }

    public EssentiaFuelEntry(List<AspectId> aspects, int value) {
        this(aspects, value, List.of(), 0);
    }

    /** A value that applies only where {@link #condition} holds. */
    public record ConditionalValue(FuelCondition condition, int value) {

        static final Codec<ConditionalValue> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(FuelCondition.CODEC.fieldOf("condition").forGetter(ConditionalValue::condition),
                        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("value")
                                .forGetter(ConditionalValue::value))
                .apply(instance, ConditionalValue::new));
    }

    /**
     * The fuel value here and now.
     *
     * <p>{@link #randomBonus} is added on top of whichever base value applied, drawn from the
     * level's own random source. This is Permutatio's "random jackpot" behaviour; the original
     * both allocated a fresh {@code Random} per call and allowed a roll of 0, which charged a
     * unit of essentia for no fuel at all (defect A-16).</p>
     */
    public int resolve(FuelEnvironment environment) {
        for (ConditionalValue candidate : conditions) {
            if (candidate.condition().test(environment)) {
                return withBonus(candidate.value(), environment);
            }
        }
        return withBonus(value, environment);
    }

    private int withBonus(int base, FuelEnvironment environment) {
        return randomBonus <= 0 ? base : base + environment.random().nextInt(randomBonus);
    }
}
