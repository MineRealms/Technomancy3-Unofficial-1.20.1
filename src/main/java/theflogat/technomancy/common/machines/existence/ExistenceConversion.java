package theflogat.technomancy.common.machines.existence;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.AmbientCreature;
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
        // The original's table gave 5 to both EnumCreatureType.creature (EntityAnimal) and
        // EnumCreatureType.ambient (EntityAmbientCreature). Only the first half was ported, so a
        // bat paid 1 instead of 5.
        if (entity instanceof Animal || entity instanceof AmbientCreature) {
            return 5;
        }
        return 1;
    }

    /** The value carried by a treasure gem: half the entity's value, at least one. */
    public static int getGem(Entity entity) {
        return Math.max(1, getValue(entity) / 2);
    }
}
