package theflogat.technomancy.common.items.technom;

import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.common.registry.TechnomItems;

/** Name-to-stack lookup for the three treasures ({@code ItemTreasure.getTreasure}). */
public final class Treasures {

    public static final String FIRE_GEM = "fireGem";
    public static final String POWER_PLATE = "powerPlate";
    public static final String GOLDEN_WING = "goldenWing";

    private Treasures() {
    }

    public static ItemStack get(String name) {
        if (FIRE_GEM.equals(name)) {
            return new ItemStack(TechnomItems.TREASURE_FIRE_GEM.get());
        }
        if (POWER_PLATE.equals(name)) {
            return new ItemStack(TechnomItems.TREASURE_POWER_PLATE.get());
        }
        if (GOLDEN_WING.equals(name)) {
            return new ItemStack(TechnomItems.TREASURE_GOLDEN_WING.get());
        }
        return ItemStack.EMPTY;
    }
}
