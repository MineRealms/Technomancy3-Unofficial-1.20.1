package theflogat.technomancy.common.items.technom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import theflogat.technomancy.common.rituals.earth.RitualExtraction;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * Gives some naturally spawned villagers a treasure and drops it when they die
 * ({@code ItemTreasure} carriers in the original). The Extraction ritual reads the same tag, and
 * a sealer can mark one so it is not lost.
 *
 * <p>Off unless {@code treasures} is enabled, which mirrors 1.7.10: the original also had this
 * behaviour behind {@code Ids.treasures}, but that flag was AND-ed with {@code treasureSafeguard},
 * which shipped false, so the feature was dormant by default there too.</p>
 */
public final class TreasureVillagers {

    private static final String[] NAMES = {Treasures.FIRE_GEM, Treasures.POWER_PLATE, Treasures.GOLDEN_WING};
    /** {@code ItemTreasure.rarity}: each type passes on {@code (rarity + 1) / 10000}. */
    private static final int[] RARITY = {75, 50, 75};
    private static final int RARITY_DENOMINATOR = 10_000;
    /** {@code getEntityData().setBoolean("treasureAttempt", true)}, written before the roll. */
    private static final String ATTEMPTED = "treasureAttempt";

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(TreasureVillagers::onVillagerJoin);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(TreasureVillagers::onVillagerDeath);
    }

    private TreasureVillagers() {
    }

    public static void onVillagerJoin(EntityJoinLevelEvent event) {
        if (!TechnomancyConfig.TREASURES.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        if (data.contains(RitualExtraction.TREASURE_TAG) || data.contains(ATTEMPTED)) {
            return;
        }
        // The original wrote treasureAttempt *before* rolling, so a villager got exactly one
        // chance in its life. Writing the marker only on success meant every chunk load rolled
        // again, and the effective rate grew with however many times the chunk was loaded - at ten
        // loads a villager was a carrier 18% of the time instead of the intended 2%.
        data.putBoolean(ATTEMPTED, true);
        int chosen = -1;
        for (int i = 0; i < RARITY.length; i++) {
            if (level.random.nextInt(RARITY_DENOMINATOR) > RARITY[i]) {
                continue;
            }
            // The original kept the rarest type of those that passed. powerPlate (50) is strictly
            // the rarest, so it wins whenever it passes, and the other two split the rest.
            if (chosen == -1 || RARITY[i] < RARITY[chosen]) {
                chosen = i;
            }
        }
        if (chosen != -1) {
            data.putString(RitualExtraction.TREASURE_TAG, NAMES[chosen]);
        }
    }

    public static void onVillagerDeath(LivingDeathEvent event) {
        if (!TechnomancyConfig.TREASURES.get()) {
            return;
        }
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
