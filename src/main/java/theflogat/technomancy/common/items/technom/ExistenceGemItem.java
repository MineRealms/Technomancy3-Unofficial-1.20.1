package theflogat.technomancy.common.items.technom;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.common.machines.existence.ExistenceConversion;

/**
 * {@code ItemExistenceGem}: the chargeable gem every Existence machine is built around.
 *
 * <p>The 1.7.10 original stored the charge in item damage with {@code setMaxDamage(100)}, where
 * a fresh gem came out at damage 100 (empty) and killing mobs drove it towards 0 (full). Modern
 * items have no metadata, so the charge lives in the stack's own tag instead: 0 is empty,
 * {@link #MAX_CHARGE} is full. The numbers are unchanged — a villager is worth
 * {@code max(1, 50 / 2) = 25} points, an animal 2, a monster 1 — so a full gem still takes four
 * villagers.</p>
 *
 * <p>The gem is purely a crafting ingredient and a charge carrier: it has no right-click
 * behaviour, exactly as in the original.</p>
 */
public final class ExistenceGemItem extends Item {

    public static final int MAX_CHARGE = 100;
    private static final String TAG_CHARGE = "charge";

    public ExistenceGemItem(Properties properties) {
        super(properties);
    }

    public static int charge(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, Math.min(MAX_CHARGE, stack.getTag().getInt(TAG_CHARGE))) : 0;
    }

    /** Adds charge, clamped to the cap. Returns how much was actually taken. */
    public static int addCharge(ItemStack stack, int amount) {
        int before = charge(stack);
        int after = Math.max(0, Math.min(MAX_CHARGE, before + amount));
        stack.getOrCreateTag().putInt(TAG_CHARGE, after);
        return after - before;
    }

    public static boolean isFull(ItemStack stack) {
        return charge(stack) >= MAX_CHARGE;
    }

    /**
     * Pours a kill into the first gem in the player's inventory that still has room
     * ({@code EventRegister} filled one gem per kill, in inventory order, and so does this).
     */
    public static void chargeFromKill(Player player, Entity killed) {
        int amount = ExistenceConversion.getGem(killed);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ExistenceGemItem && charge(stack) < MAX_CHARGE) {
                addCharge(stack, amount);
                return;
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFull(stack);
    }

    /** The charge bar: a full gem reads as full rather than as damage. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        int charge = charge(stack);
        return charge > 0 && charge < MAX_CHARGE;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * charge(stack) / MAX_CHARGE);
    }
}
