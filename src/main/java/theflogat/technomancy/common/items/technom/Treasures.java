package theflogat.technomancy.common.items.technom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.common.registry.TechnomItems;

/** Name-to-stack lookup for the three treasures ({@code ItemTreasure.getTreasure}). */
public final class Treasures {

    public static final String FIRE_GEM = "fireGem";
    public static final String POWER_PLATE = "powerPlate";
    public static final String GOLDEN_WING = "goldenWing";

    private Treasures() {
    }

    /**
     * The item behind a treasure name, or {@code null} when the name is not one of the three.
     * The original's {@code getTreasure} returned null for an unknown name too.
     */
    public static TreasureItem item(String name) {
        Item item;
        if (FIRE_GEM.equals(name)) {
            item = TechnomItems.TREASURE_FIRE_GEM.get();
        } else if (POWER_PLATE.equals(name)) {
            item = TechnomItems.TREASURE_POWER_PLATE.get();
        } else if (GOLDEN_WING.equals(name)) {
            item = TechnomItems.TREASURE_GOLDEN_WING.get();
        } else {
            return null;
        }
        return item instanceof TreasureItem treasure ? treasure : null;
    }

    public static ItemStack get(String name) {
        TreasureItem item = item(name);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
