package theflogat.technomancy.common.rituals.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.technom.Treasures;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * {@code RitualExtraction}: pulls a treasure back out of the villager carrying it. A villager
 * that has none in range means the ritual instead detonates, as the original did.
 */
public class RitualExtraction extends Ritual {

    public static final String TREASURE_TAG = "technom:treasure";

    public RitualExtraction() {
        super(new Type[] {Type.DARK, Type.DARK, Type.LIGHT}, Type.EARTH);
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        AABB box = new AABB(pos.getX() - 5, pos.getY() - 5, pos.getZ() - 5,
                pos.getX() + 6, pos.getY() + 6, pos.getZ() + 6);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
            if (!villager.getPersistentData().contains(TREASURE_TAG)) {
                continue;
            }
            String name = villager.getPersistentData().getString(TREASURE_TAG);
            ItemStack treasure = Treasures.get(name);
            if (treasure.isEmpty()) {
                Technomancy.LOGGER.error("Unknown treasure '{}' on a villager at {}", name, villager.blockPosition());
                return;
            }
            villager.getPersistentData().remove(TREASURE_TAG);
            villager.discard();
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, treasure));
            removeFrame(level, pos);
            return;
        }
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F,
                Level.ExplosionInteraction.BLOCK);
    }
}
