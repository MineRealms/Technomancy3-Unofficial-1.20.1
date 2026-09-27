package theflogat.technomancy.common.wands;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.Technomancy;

/**
 * What counts as a wrench on a Technomancy machine: anything in {@code technom:tools/wrench}, plus
 * a wand or sceptre built on the technoturge rod.
 *
 * <p>The rod cannot be expressed as an item tag, because the wrench is not the item
 * ({@code thaumcraft:wand}, shared with every other wand) but the rod stored on the stack. This is
 * the reduced form of {@code ItemTechnoturgeScepter}, which implemented seven mod-specific wrench
 * interfaces plus vanilla block rotation; none of those mods exist on 1.20.1 and Forge has no
 * common wrench contract, so only the mod's own machines are served.</p>
 *
 * <p>{@code EssentiaDynamoBlock.WRENCHES} names the same tag and predates this class; the two
 * should be folded together when the feature groups merge.</p>
 */
public final class TechnomWrench {

    public static final TagKey<Item> WRENCHES =
            TagKey.create(Registries.ITEM, new ResourceLocation(Technomancy.MOD_ID, "tools/wrench"));

    private TechnomWrench() {
    }

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(WRENCHES) || TechnomWandRods.hasRod(stack, TechnomWandRods.TECHNOTURGE));
    }
}
