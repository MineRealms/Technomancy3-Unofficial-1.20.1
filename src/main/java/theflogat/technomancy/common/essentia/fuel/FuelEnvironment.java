package theflogat.technomancy.common.essentia.fuel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Everything a {@link FuelCondition} is allowed to ask about the place a dynamo stands in.
 *
 * <p>This interface exists so the fuel table can be resolved without a world: conditions are
 * pure functions of these facts, which makes every branch of the table unit-testable and keeps
 * world access in one place instead of spread across the condition classes.</p>
 *
 * <p>Biomes and dimensions are named by {@link ResourceLocation} rather than by {@code TagKey}
 * or {@code ResourceKey}: registry-typed keys are the world's business, and this is the
 * boundary where a name from a data pack becomes one. It also keeps the condition codecs free
 * of registry-backed codecs, which cannot even be constructed before the game has started.</p>
 */
public interface FuelEnvironment {

    /** @param tag id of a {@code worldgen/biome} tag */
    boolean inBiomeTag(ResourceLocation tag);

    /** @param dimension id of a level, e.g. {@code minecraft:the_end} */
    boolean inDimension(ResourceLocation dimension);

    /** Block height of the dynamo. Absolute, so 1.20.1's y = -64 floor is visible to conditions. */
    int height();

    /** Sea level of this dimension, i.e. the anchor the original's absolute 60 really meant. */
    int seaLevel();

    boolean slimeChunk();

    boolean daytime();

    /**
     * The world's random source. Must be the level's own, never a fresh {@code Random}: the
     * original allocated one per call, which is both wasteful and unseeded relative to the
     * world (defect A-16).
     */
    RandomSource random();

    static FuelEnvironment of(Level level, BlockPos pos) {
        return new LevelFuelEnvironment(level, pos);
    }

    /** Reads the facts straight off a live level. */
    record LevelFuelEnvironment(Level level, BlockPos pos) implements FuelEnvironment {

        /** TC4's slime-chunk salt; the same constant Minecraft uses for slime spawning. */
        private static final long SLIME_SALT = 987234911L;

        @Override
        public boolean inBiomeTag(ResourceLocation tag) {
            // TagKey.create interns, so this is a lookup rather than an allocation, and it only
            // runs when a charge of essentia is actually being priced.
            return level.getBiome(pos).is(TagKey.create(Registries.BIOME, tag));
        }

        @Override
        public boolean inDimension(ResourceLocation dimension) {
            return level.dimension().location().equals(dimension);
        }

        @Override
        public int height() {
            return pos.getY();
        }

        @Override
        public int seaLevel() {
            return level.getSeaLevel();
        }

        @Override
        public boolean slimeChunk() {
            // Only a server level knows the world seed, and fuel values are only ever resolved
            // server-side; without a seed the honest answer is "not a slime chunk".
            if (!(level instanceof ServerLevel server)) {
                return false;
            }
            return WorldgenRandom.seedSlimeChunk(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()), server.getSeed(), SLIME_SALT)
                    .nextInt(10) == 0;
        }

        @Override
        public boolean daytime() {
            return level.isDay();
        }

        @Override
        public RandomSource random() {
            return level.random;
        }
    }
}
