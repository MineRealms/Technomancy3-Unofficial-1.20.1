package theflogat.technomancy.common.machines.existence;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * How much Existence one entity is worth ({@code ExistenceConversion}): villagers are worth the
 * most, monsters the least, everything else a token one. The original keyed this on entity
 * classes; the modern equivalent asks by supertype.
 */
public final class ExistenceConversion {

    private ExistenceConversion() {
    }

    public static int getValue(Entity entity) {
        if (entity instanceof AbstractVillager) {
            return 50;
        }
        if (entity instanceof Animal) {
            return 5;
        }
        if (entity instanceof Mob) {
            return 1;
        }
        return 1;
    }

    /** The value carried by a treasure gem: half the entity's value, at least one. */
    public static int getGem(Entity entity) {
        return Math.max(1, getValue(entity) / 2);
    }
}
