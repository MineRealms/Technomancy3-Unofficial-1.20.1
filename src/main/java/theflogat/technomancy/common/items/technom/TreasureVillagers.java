package theflogat.technomancy.common.items.technom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import theflogat.technomancy.common.rituals.earth.RitualExtraction;

/**
 * Gives some naturally spawned villagers a treasure and drops it when they die
 * ({@code ItemTreasure} carriers in the original). The Extraction ritual reads the same tag, and
 * a sealer can mark one so it is not lost.
 */
public final class TreasureVillagers {

    private static final String[] NAMES = {Treasures.FIRE_GEM, Treasures.POWER_PLATE, Treasures.GOLDEN_WING};
    /** One villager in this many is a carrier. */
    private static final int RARITY = 50;

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(TreasureVillagers::onVillagerJoin);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(TreasureVillagers::onVillagerDeath);
    }

    private TreasureVillagers() {
    }

    public static void onVillagerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        if (data.contains(RitualExtraction.TREASURE_TAG)) {
            return;
        }
        if (level.random.nextInt(RARITY) == 0) {
            data.putString(RitualExtraction.TREASURE_TAG, NAMES[level.random.nextInt(NAMES.length)]);
        }
    }

    public static void onVillagerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        if (data.getBoolean("seal")) {
            return;
        }
        String name = data.getString(RitualExtraction.TREASURE_TAG);
        if (name.isEmpty()) {
            return;
        }
        ItemStack treasure = Treasures.get(name);
        if (!treasure.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getY() + 0.5,
                    villager.getZ(), treasure));
        }
    }
}
