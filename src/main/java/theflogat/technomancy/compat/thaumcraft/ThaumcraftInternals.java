package theflogat.technomancy.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import dev.tc4port.thaumcraft.block.entity.AlchemyFurnaceBlockEntity;
import dev.tc4port.thaumcraft.block.entity.InfusionMatrixBlockEntity;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * The two places where 1.20.1 TC4R exposes a value but no way to change it, so the original's
 * behaviour cannot be written against the public API alone.
 *
 * <p>Both are TC4R's own classes, whose field names survive reobfuscation (only Minecraft is
 * remapped), so looking the fields up by name works in a development run and in a packed mod
 * alike. Every lookup is done once, at class load, and a miss disables the feature that needed
 * it instead of throwing during play: the flux lamp then still stores ordo and produces flux
 * goo, and the electric bellows then still stokes vanilla furnaces.</p>
 *
 * <p>Nothing else in the mod reads TC4R's internals; keep it that way.</p>
 */
public final class ThaumcraftInternals {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static final Field INSTABILITY = find(InfusionMatrixBlockEntity.class, "instability");
    @Nullable
    private static final Field BURN_TIME = find(AlchemyFurnaceBlockEntity.class, "burnTime");
    @Nullable
    private static final Field SPEED_BOOST = find(AlchemyFurnaceBlockEntity.class, "speedBoost");

    private ThaumcraftInternals() {
    }

    /** Whether the flux lamp can actually calm an altar on this TC4R build. */
    public static boolean canStabilise() {
        return INSTABILITY != null;
    }

    /** Whether the electric bellows can actually stoke an alchemical furnace. */
    public static boolean canStoke() {
        return BURN_TIME != null && SPEED_BOOST != null;
    }

    /**
     * Lowers a running altar's instability, which is what the flux lamp exists to do
     * ({@code TileFluxLamp} decremented the same field).
     *
     * @return whether the reduction was applied
     */
    public static boolean reduceInstability(InfusionMatrixBlockEntity matrix, int by) {
        Field field = INSTABILITY;
        if (field == null) {
            return false;
        }
        try {
            int current = field.getInt(matrix);
            if (current <= 0) {
                return false;
            }
            field.setInt(matrix, Math.max(0, current - by));
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.error("Technomancy could not write TC4R instability", e);
            return false;
        }
    }

    /**
     * Puts fuel and the boosted flag on an alchemical furnace, which is the one thing TC4R's
     * {@code ArcaneBellowsApi} has no registration point for.
     *
     * @return whether the furnace was stoked
     */
    public static boolean stoke(AlchemyFurnaceBlockEntity furnace, int burnTime) {
        Field burn = BURN_TIME;
        Field boost = SPEED_BOOST;
        if (burn == null || boost == null) {
            return false;
        }
        try {
            burn.setInt(furnace, burnTime);
            boost.setBoolean(furnace, true);
            return true;
        } catch (IllegalAccessException e) {
            LOGGER.error("Technomancy could not stoke TC4R's alchemical furnace", e);
            return false;
        }
    }

    @Nullable
    private static Field find(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            LOGGER.error("Technomancy could not reach {}.{}; the feature that needs it is disabled",
                    owner.getSimpleName(), name);
            return null;
        }
    }
}
