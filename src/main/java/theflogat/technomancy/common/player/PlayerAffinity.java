package theflogat.technomancy.common.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative affinity and existence progress ({@code PlayerData}), stored on the
 * player's Forge persistent data so it survives death and dimension changes.
 *
 * <p>The growth model is the original's: a roll against {@code value * 10 - residual} raises the
 * value and clears the residual on success, and only raises the residual otherwise, so progress
 * is never lost. Existence uses the same shape at {@code level * 100 - residual}.</p>
 *
 * <p>Two defects from the original are fixed here: the affinity {@code id} bug lives in
 * {@link Affinity}, and {@code addExistencePower(World, String)} dereferenced a possibly absent
 * player. This class only ever takes a live {@link Player}.</p>
 */
public final class PlayerAffinity {

    private static final String ROOT = "technom";
    private static final String RESIDUAL_SUFFIX = "_residual";
    private static final String EXISTENCE_LEVEL = "technom:existence_level";
    private static final String EXISTENCE_RESIDUAL = "technom:existence_residual";
    private static final String EXISTENCE_POWER = "technom:existence_power";

    private PlayerAffinity() {
    }

    private static CompoundTag data(Player player) {
        return player.getPersistentData();
    }

    private static CompoundTag root(Player player) {
        CompoundTag data = data(player);
        if (!data.contains(ROOT)) {
            data.put(ROOT, new CompoundTag());
        }
        return data.getCompound(ROOT);
    }

    /** Fills in the defaults the original's {@code prepareData} wrote on first login. */
    public static void prepare(Player player) {
        CompoundTag root = root(player);
        for (Affinity affinity : Affinity.values()) {
            if (affinity == Affinity.NORMAL) {
                continue;
            }
            if (!root.contains(affinity.key())) {
                root.putInt(affinity.key(), 1);
                root.putInt(affinity.key() + RESIDUAL_SUFFIX, 0);
            }
        }
        if (!root.contains(EXISTENCE_LEVEL)) {
            root.putInt(EXISTENCE_LEVEL, 1);
            root.putInt(EXISTENCE_RESIDUAL, 0);
        }
        if (!root.contains(EXISTENCE_POWER)) {
            root.putInt(EXISTENCE_POWER, 1);
        }
    }

    public static int value(Player player, Affinity affinity) {
        CompoundTag root = root(player);
        return root.contains(affinity.key()) ? root.getInt(affinity.key()) : 1;
    }

    public static int residual(Player player, Affinity affinity) {
        CompoundTag root = root(player);
        return root.getInt(affinity.key() + RESIDUAL_SUFFIX);
    }

    /** One growth roll per iteration, as the original's {@code addAffinity} loop did. */
    public static void add(net.minecraft.world.level.Level level, Player player, Affinity affinity, int times) {
        if (affinity == null || affinity == Affinity.NORMAL) {
            return;
        }
        CompoundTag root = root(player);
        RandomSource random = level.getRandom();
        for (int i = 0; i < times; i++) {
            int value = Math.max(1, root.getInt(affinity.key()));
            int residual = Math.max(0, root.getInt(affinity.key() + RESIDUAL_SUFFIX));
            int threshold = value * 10 - residual;
            if (threshold <= 0 || random.nextInt(threshold) == 0) {
                root.putInt(affinity.key(), value + 1);
                root.putInt(affinity.key() + RESIDUAL_SUFFIX, 0);
            } else {
                root.putInt(affinity.key() + RESIDUAL_SUFFIX, residual + 1);
            }
        }
    }

    public static int existenceLevel(Player player) {
        CompoundTag root = root(player);
        return root.contains(EXISTENCE_LEVEL) ? root.getInt(EXISTENCE_LEVEL) : 1;
    }

    public static int existencePower(Player player) {
        CompoundTag root = root(player);
        return root.contains(EXISTENCE_POWER) ? root.getInt(EXISTENCE_POWER) : 1;
    }

    public static void addExistencePower(RandomSource random, Player player) {
        prepare(player);
        CompoundTag root = root(player);
        int level = Math.max(1, root.getInt(EXISTENCE_LEVEL));
        int residual = Math.max(0, root.getInt(EXISTENCE_RESIDUAL));
        int threshold = level * 100 - residual;
        if (threshold <= 0 || random.nextInt(threshold) == 0) {
            root.putInt(EXISTENCE_LEVEL, level + 1);
            root.putInt(EXISTENCE_RESIDUAL, 0);
        } else {
            root.putInt(EXISTENCE_RESIDUAL, residual + 1);
        }
    }

    /**
     * The affinity the player is aligned with: the one holding more than half of the total,
     * and only once existence has reached 20. Otherwise {@link Affinity#NORMAL}.
     */
    public static Affinity dominant(Player player) {
        if (existenceLevel(player) < 20) {
            return Affinity.NORMAL;
        }
        int total = 0;
        for (Affinity affinity : Affinity.values()) {
            if (affinity != Affinity.NORMAL) {
                total += value(player, affinity);
            }
        }
        for (Affinity affinity : Affinity.values()) {
            if (affinity != Affinity.NORMAL && value(player, affinity) > total / 2) {
                return affinity;
            }
        }
        return Affinity.NORMAL;
    }
}
