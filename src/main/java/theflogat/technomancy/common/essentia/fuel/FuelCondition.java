package theflogat.technomancy.common.essentia.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import theflogat.technomancy.Technomancy;

/**
 * A test on the dynamo's surroundings that can raise an aspect's fuel value.
 *
 * <p>Every condition the 1.7.10 table used was a hard-coded reference to a 1.7.10 world: a
 * {@code BiomeGenBase} identity comparison, {@code BiomeGenBase.sky} for the End, and two
 * absolute heights. All of those are expressed here as data - biome tags, a dimension registry
 * key, and a height relative to a named anchor - so a data pack can retarget them without a
 * code change, and so 1.20.1's y = -64 floor cannot silently change their meaning
 * (defect A-17).</p>
 *
 * <p>Conditions are pure functions of a {@link FuelEnvironment}; they never touch a level
 * directly, and they name biome tags and dimensions by id rather than by registry-typed key -
 * turning an id into a {@code TagKey} is the environment's job, and a registry-backed codec
 * cannot be built at all until the game has started.</p>
 */
public sealed interface FuelCondition {

    /** Dispatches on a {@code "type"} field, with each condition's own fields inlined beside it. */
    Codec<FuelCondition> CODEC = ResourceLocation.CODEC.<FuelCondition>partialDispatch("type",
            condition -> DataResult.success(condition.typeId()), FuelCondition::codecFor);

    boolean test(FuelEnvironment environment);

    ResourceLocation typeId();

    // ---- type registry ----

    ResourceLocation BIOME_TAG = id("biome_tag");
    ResourceLocation DIMENSION = id("dimension");
    ResourceLocation HEIGHT = id("height");
    ResourceLocation SLIME_CHUNK = id("slime_chunk");
    ResourceLocation DAYTIME = id("daytime");

    Map<ResourceLocation, MapCodec<? extends FuelCondition>> TYPES = Map.of(
            BIOME_TAG, BiomeTag.MAP_CODEC,
            DIMENSION, Dimension.MAP_CODEC,
            HEIGHT, Height.MAP_CODEC,
            SLIME_CHUNK, SlimeChunk.MAP_CODEC,
            DAYTIME, Daytime.MAP_CODEC);

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Technomancy.MOD_ID, path);
    }

    private static DataResult<? extends Codec<? extends FuelCondition>> codecFor(ResourceLocation type) {
        MapCodec<? extends FuelCondition> codec = TYPES.get(type);
        return codec == null
                ? DataResult.error(() -> "Unknown essentia fuel condition type: " + type)
                : DataResult.success(codec.codec());
    }

    // ---- conditions ----

    /** The dynamo stands in a biome carrying the given {@code worldgen/biome} tag. */
    record BiomeTag(ResourceLocation tag) implements FuelCondition {

        static final MapCodec<BiomeTag> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(ResourceLocation.CODEC.fieldOf("tag").forGetter(BiomeTag::tag))
                .apply(instance, BiomeTag::new));

        @Override
        public boolean test(FuelEnvironment environment) {
            return environment.inBiomeTag(tag);
        }

        @Override
        public ResourceLocation typeId() {
            return BIOME_TAG;
        }
    }

    /** The dynamo is in the given dimension; the original's {@code BiomeGenBase.sky} test. */
    record Dimension(ResourceLocation dimension) implements FuelCondition {

        static final MapCodec<Dimension> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(ResourceLocation.CODEC.fieldOf("dimension").forGetter(Dimension::dimension))
                .apply(instance, Dimension::new));

        @Override
        public boolean test(FuelEnvironment environment) {
            return environment.inDimension(dimension);
        }

        @Override
        public ResourceLocation typeId() {
            return DIMENSION;
        }
    }

    /**
     * The dynamo is above and/or below a height, measured from an anchor.
     *
     * <p>Bounds are exclusive, which is what reads naturally for the two cases the table
     * needs: "below sea level" and "above 150".</p>
     */
    record Height(Anchor anchor, Optional<Integer> above, Optional<Integer> below) implements FuelCondition {

        static final MapCodec<Height> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(FuelCodecs.strictOptionalField(Anchor.CODEC, "anchor", Anchor.ABSOLUTE)
                                .forGetter(Height::anchor),
                        FuelCodecs.strictOptionalField(Codec.INT, "above").forGetter(Height::above),
                        FuelCodecs.strictOptionalField(Codec.INT, "below").forGetter(Height::below))
                .apply(instance, Height::new));

        public Height {
            if (above.isEmpty() && below.isEmpty()) {
                throw new IllegalArgumentException("a height condition needs at least one of 'above', 'below'");
            }
        }

        /** What an offset is measured from. */
        public enum Anchor implements StringRepresentable {
            /** Raw block height, so the value has to be chosen for a world whose floor is -64. */
            ABSOLUTE("absolute"),
            /**
             * Offset from {@code level.getSeaLevel()} (63 in the overworld). The original's
             * {@code yCoord < 60} meant "below the surface", which only an anchored value still
             * means once the buildable range starts at -64.
             */
            SEA_LEVEL("sea_level");

            static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);

            private final String name;

            Anchor(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        @Override
        public boolean test(FuelEnvironment environment) {
            int origin = anchor == Anchor.SEA_LEVEL ? environment.seaLevel() : 0;
            int y = environment.height();
            return above.map(offset -> y > origin + offset).orElse(true)
                    && below.map(offset -> y < origin + offset).orElse(true);
        }

        @Override
        public ResourceLocation typeId() {
            return HEIGHT;
        }
    }

    /** The dynamo's chunk is a slime chunk, using Minecraft's own slime-chunk seed. */
    record SlimeChunk() implements FuelCondition {

        static final MapCodec<SlimeChunk> MAP_CODEC = MapCodec.unit(SlimeChunk::new);

        @Override
        public boolean test(FuelEnvironment environment) {
            return environment.slimeChunk();
        }

        @Override
        public ResourceLocation typeId() {
            return SLIME_CHUNK;
        }
    }

    /** Daytime ({@code day = true}) or night ({@code day = false}). */
    record Daytime(boolean day) implements FuelCondition {

        static final MapCodec<Daytime> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(FuelCodecs.strictOptionalField(Codec.BOOL, "day", true).forGetter(Daytime::day))
                .apply(instance, Daytime::new));

        @Override
        public boolean test(FuelEnvironment environment) {
            return environment.daytime() == day;
        }

        @Override
        public ResourceLocation typeId() {
            return DAYTIME;
        }
    }
}
