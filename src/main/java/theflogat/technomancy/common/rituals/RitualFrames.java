package theflogat.technomancy.common.rituals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.blocks.technom.CrystalBlock;

/**
 * The ritual frame geometry ({@code RitualHelper}): three tiers of four crystal pillars.
 *
 * <p>Tier {@code t} sits at radius {@code 1 + 2t} on both axes — 1, 3 and 5 — and every pillar
 * is three crystals tall. The catalyst is the centre the ring is measured from, so the corners
 * are {@code (x ± r, y + 0..2, z ± r)}. Keeping the check and the removal in one class is
 * deliberate: the original had {@code checkForT} and {@code removeT} in a helper that used two
 * subtly different step expressions, and a mismatch there would refuse a ring it could not
 * take down.</p>
 */
public final class RitualFrames {

    private RitualFrames() {
    }

    /** Radius of a tier's ring. */
    public static int radius(int tier) {
        return 1 + 2 * tier;
    }

    /** Whether the whole tier is the given crystal kind. */
    public static boolean matches(Level level, BlockPos centre, int tier, Ritual.Type type) {
        int radius = radius(tier);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -radius; dx <= radius; dx += 2 * radius) {
                for (int dz = -radius; dz <= radius; dz += 2 * radius) {
                    BlockPos corner = centre.offset(dx, dy, dz);
                    if (!(level.getBlockState(corner).getBlock() instanceof CrystalBlock crystal)
                            || crystal.type() != type) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Whether the whole tier is clear of every crystal kind, as a {@code null} frame demands. */
    public static boolean isTierEmpty(Level level, BlockPos centre, int tier) {
        int radius = radius(tier);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -radius; dx <= radius; dx += 2 * radius) {
                for (int dz = -radius; dz <= radius; dz += 2 * radius) {
                    if (level.getBlockState(centre.offset(dx, dy, dz)).getBlock() instanceof CrystalBlock) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Clears a tier's ring after the ritual consumed it. */
    public static void remove(Level level, BlockPos centre, int tier) {
        int radius = radius(tier);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -radius; dx <= radius; dx += 2 * radius) {
                for (int dz = -radius; dz <= radius; dz += 2 * radius) {
                    level.removeBlock(centre.offset(dx, dy, dz), false);
                }
            }
        }
    }
}
