package theflogat.technomancy.common.items.technom;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.player.Affinity;
import theflogat.technomancy.common.player.PlayerAffinity;

/**
 * One of the three treasures ({@code ItemTreasure}): fire gem, power plate and golden wing.
 * While carried it grants a potion effect and slowly raises its affinity.
 *
 * <p>The original packed the three into metadata 0..2; here each is its own item, so the
 * {@code getTreasure(name)} lookup is a plain registry rather than a damage value.</p>
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
}
