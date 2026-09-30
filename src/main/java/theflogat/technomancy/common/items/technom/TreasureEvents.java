package theflogat.technomancy.common.items.technom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import theflogat.technomancy.common.rituals.earth.RitualExtraction;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The treasure carriers: which villagers spawn holding a treasure, and what a carried treasure
 * does when its carrier is hurt or dies ({@code ItemTreasure} carriers in the original).
 *
 * <p>One villager in fifty becomes a carrier when it first joins the world. An unsealed carrier
 * retaliates when it is hurt, and so does a player carrying a treasure. A carrier that dies either
 * detonates - the treasure is destroyed, not dropped - or, if it was sealed, drops the treasure
 * intact. The Extraction ritual reads the same tag, and the Existence sealing device sets the seal
 * that makes a carrier survivable.</p>
 *
 * <p>Off unless {@code treasures} is enabled, which mirrors 1.7.10: the original also had this
 * behaviour behind {@code Ids.treasures}, but that flag was AND-ed with {@code treasureSafeguard},
 * which shipped false, so the feature was dormant by default there too.</p>
 */
public final class TreasureEvents {

    private static final String[] NAMES = {Treasures.FIRE_GEM, Treasures.POWER_PLATE, Treasures.GOLDEN_WING};
    /** {@code ItemTreasure.rarity}: each type passes on {@code (rarity + 1) / 10000}. */
    private static final int[] RARITY = {75, 50, 75};
    private static final int RARITY_DENOMINATOR = 10_000;
    /** {@code getEntityData().setBoolean("treasureAttempt", true)}, written before the roll. */
    private static final String ATTEMPTED = "treasureAttempt";
    /** {@code "seal"}: set by the Existence sealing device; the original cleared it after 80 ticks. */
    private static final String SEALED = "seal";

    public static void register() {
        // All three were @SubscribeEvent(priority = EventPriority.LOW) upstream, so they are
        // registered at LOW here too. Note what LOW does and does not buy: it is dispatch order
        // only. The cancellation filter is the separate receiveCanceled flag, which these
        // registrations leave false, so an already-cancelled hurt still skips this handler - at
        // any priority, since LOW runs last of all.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, TreasureEvents::onVillagerJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, TreasureEvents::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, TreasureEvents::onVillagerDeath);
    }

    private TreasureEvents() {
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
            // The original kept the rarest type of those that passed, comparing with a strict
            // less-than. powerPlate (50) is strictly the rarest, so it wins whenever it passes.
            // fireGem and goldenWing both sit at 75, and the strict comparison means the earlier
            // index wins that tie, so a 75/75 roll goes to fireGem rather than being split.
            if (chosen == -1 || RARITY[i] < RARITY[chosen]) {
                chosen = i;
            }
        }
        if (chosen != -1) {
            data.putString(RitualExtraction.TREASURE_TAG, NAMES[chosen]);
        }
    }

    /**
     * {@code EventRegister.onHurt}: a carrier that is being hurt retaliates, and so does a player
     * carrying a treasure. The original ran this at LOW priority, which only matters if another
     * handler cancels the hurt first.
     */
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!TechnomancyConfig.TREASURES.get() || event.getEntity().level().isClientSide) {
            return;
        }
        applyWard(event.getEntity(), event);
    }

    /**
     * The retaliation itself, without the config gate, so a test can drive it directly. A villager
     * carrier only retaliates while it is unsealed; a player retaliates once per treasure carried,
     * which is what the original's per-slot loop did - and it counted any treasure, not just one
     * with a particular metadata, the same call {@code ExistenceGemItem.hasAny} made.
     */
    public static void applyWard(LivingEntity victim, LivingHurtEvent event) {
        if (victim instanceof Villager villager) {
            CompoundTag data = villager.getPersistentData();
            if (data.getBoolean(SEALED)) {
                return;
            }
            TreasureItem treasure = Treasures.item(data.getString(RitualExtraction.TREASURE_TAG));
            if (treasure != null) {
                TreasureItem.onUserHit(treasure.affinity(), event);
            }
            return;
        }
        if (victim instanceof Player player) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof TreasureItem treasure) {
                    TreasureItem.onUserHit(treasure.affinity(), event);
                }
            }
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
        settleDeath(level, villager);
    }

    /**
     * What a carrier's death leaves behind. Unsealed, the treasure is destroyed and takes its
     * revenge ({@link TreasureItem#onTreasureDestroyed}); sealed, it survives and drops.
     *
     * <p>Dropping at all is a port addition - the original villagers never dropped an item, because
     * the Extraction ritual was the only way to take a treasure - so the port keeps that path and
     * makes the seal the thing that preserves it. Reading {@code seal} as "this treasure is
     * preserved" is also the only reading under which the sealer is useful: the original cleared
     * the flag after 80 ticks, and the port never clears it, so a sealed carrier stays lootable.</p>
     */
    public static void settleDeath(ServerLevel level, Villager villager) {
        CompoundTag data = villager.getPersistentData();
        TreasureItem treasure = Treasures.item(data.getString(RitualExtraction.TREASURE_TAG));
        if (treasure == null) {
            return;
        }
        // Consume the carrier before acting. LivingDeathEvent is cancelable, so a mod that
        // resurrects the villager after this LOW listener runs would otherwise leave it carrying
        // the treasure again - free to drop a second one, or to detonate a second time.
        boolean sealed = data.getBoolean(SEALED);
        data.remove(RitualExtraction.TREASURE_TAG);
        data.remove(SEALED);
        if (sealed) {
            level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getY() + 0.5,
                    villager.getZ(), new ItemStack(treasure)));
            return;
        }
        TreasureItem.onTreasureDestroyed(treasure.affinity(), level, villager);
    }
}
