package theflogat.technomancy.common.items.technom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import theflogat.technomancy.common.player.Affinity;
import theflogat.technomancy.common.player.PlayerAffinity;

/**
 * One of the three treasures ({@code ItemTreasure}): fire gem, power plate and golden wing.
 * While carried it grants a potion effect and slowly raises its affinity; while its carrier is
 * being hurt, or dies, it hits back ({@link #onUserHit}, {@link #onTreasureDestroyed}).
 *
 * <p>The original packed the three into metadata 0..2; here each is its own item, so the
 * {@code getTreasure(name)} lookup is a plain registry rather than a damage value. The two effect
 * methods below switch on the affinity, which is the same 0/1/2 mapping the damage value was:
 * fire gem = FIRE, power plate = DARK, golden wing = LIGHT.</p>
 */
public final class TreasureItem extends Item {

    private final Affinity affinity;

    public TreasureItem(Affinity affinity, Properties properties) {
        super(properties);
        this.affinity = affinity;
    }

    public Affinity affinity() {
        return affinity;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof Player player) || level.isClientSide) {
            return;
        }
        switch (affinity) {
            case FIRE -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
            case DARK -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false));
            case LIGHT -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
            default -> { }
        }
        if (level.getGameTime() % 20 == 0) {
            PlayerAffinity.add(level, player, affinity, 1);
        }
    }

    /**
     * {@code ItemTreasure.onUserHit}: the treasure's answer to its holder being hurt. Fire gem
     * swallows fire damage and sets the attacker alight, power plate grants a one-hit Resistance V,
     * golden wing launches the attacker upwards.
     *
     * <p>Who gets hit is the caller's business - the original only ran this for an unsealed
     * villager carrier or for a player carrying a treasure - so this method is the effect only.</p>
     */
    public static void onUserHit(Affinity affinity, LivingHurtEvent event) {
        DamageSource source = event.getSource();
        // The 1.12 baseline read getTrueSource(), the entity responsible for the damage - the
        // archer behind an arrow, not the arrow. DamageSource.getEntity() is that method here, and
        // it is the reading that makes "ignite whoever hit you" true for ranged attacks.
        Entity attacker = source.getEntity();
        switch (affinity) {
            case FIRE -> {
                if (source.is(DamageTypeTags.IS_FIRE)) {
                    event.setCanceled(true);
                }
                if (attacker != null) {
                    attacker.setSecondsOnFire(15);
                }
            }
            // The 3-argument constructor keeps upstream's showParticles=true. Duration 1 tick is
            // deliberate: the effect is read when the incoming damage is reduced, a few lines later
            // in the same hurt, so this is a single-hit shield and nothing more.
            case DARK -> event.getEntity().addEffect(
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1, 4));
            case LIGHT -> {
                if (attacker != null) {
                    fling(attacker, 3.0D, 2.1D);
                }
            }
            default -> { }
        }
    }

    /**
     * {@code ItemTreasure.onTreasureDestroyed}: what an unsealed carrier's treasure does when the
     * carrier dies, instead of being dropped. Fire gem detonates a radius-30 explosion and burns
     * everything within 25; power plate slows everything within 5 and turns every breakable block
     * within 5 into obsidian; golden wing launches everything within 25 upwards.
     */
    public static void onTreasureDestroyed(Affinity affinity, ServerLevel level, LivingEntity carrier) {
        double x = carrier.getX();
        double y = carrier.getY();
        double z = carrier.getZ();
        switch (affinity) {
            case FIRE -> {
                level.explode(carrier, x, y, z, 30.0F, Level.ExplosionInteraction.BLOCK);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around(x, y, z, 25))) {
                    if (!entity.isInvulnerable()) {
                        entity.setSecondsOnFire(120);
                    }
                }
            }
            case DARK -> {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around(x, y, z, 5))) {
                    if (!entity.isInvulnerable()) {
                        // 1 tick at amplifier 2, as upstream: it lands but is gone the same tick,
                        // so the visible half of this effect is the obsidian, not the fatigue.
                        entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1, 2));
                    }
                }
                // The original cast the double coordinates to int, so truncation rather than floor.
                int cx = (int) x;
                int cy = (int) y;
                int cz = (int) z;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                            // -1 is the unbreakable sentinel, so bedrock and friends survive. Air
                            // reads as 0, not -1, so the whole 11x11x11 region ends up obsidian -
                            // a solid cube, which is what the original produced too.
                            if (level.getBlockState(pos).getDestroySpeed(level, pos) != -1.0F) {
                                level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
            case LIGHT -> {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around(x, y, z, 25))) {
                    fling(entity, 3.0D, 8.0D);
                }
            }
            default -> { }
        }
    }

    /** The original built this box from the carrier's coordinates, not from its bounding box. */
    private static AABB around(double x, double y, double z, int radius) {
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    /**
     * The original's knockback: horizontal {@code -sin(a) * k * 0.5} / {@code cos(a) * k * 0.5} and
     * a fixed vertical, with {@code k = 3} and {@code a = (PI - yaw) * PI / 180}. That angle mixes
     * radians and degrees - {@code PI} is not 180 - so the horizontal direction only loosely
     * follows the target's facing, though the horizontal magnitude is always {@code k / 2 = 1.5}.
     * It is upstream's literal arithmetic, kept rather than silently "fixed".
     */
    private static void fling(Entity entity, double horizontal, double vertical) {
        float angle = (float) (Math.PI - entity.getYRot()) * (float) Math.PI / 180.0F;
        // push() sets hasImpulse, which is what makes ServerEntity.sendChanges emit the motion
        // packet, so a knocked-back player is moved on their own client too - no extra sync needed.
        entity.push(-Mth.sin(angle) * horizontal * 0.5D, vertical, Mth.cos(angle) * horizontal * 0.5D);
    }
}
