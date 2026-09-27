package theflogat.technomancy.common.wands;

import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandMaterialId;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.Technomancy;

/**
 * The two Technomancy wand rods and the per-stack energy buffer they share.
 *
 * <p>The rods themselves are data: {@code data/thaumcraft/data_maps/item/wand_rods.json} plus the
 * {@code #thaumcraft:wand_rods} tag, so TC4R's own wand and sceptre assembly, costs, research
 * gates and rendering apply to them unchanged. Only the charging behaviour is code.</p>
 */
public final class TechnomWandRods {

    /** {@code WandRod("electric", 25, wandCores:0, 10, new ElectricWandUpdate(), ..)}. */
    public static final WandMaterialId ELECTRIC = WandMaterialId.of(new ResourceLocation(Technomancy.MOD_ID, "electric"));
    /** {@code WandRod("technoturge", 100, wandCores:1, 11, ..)}. */
    public static final WandMaterialId TECHNOTURGE = WandMaterialId.of(new ResourceLocation(Technomancy.MOD_ID, "technoturge"));

    /**
     * Energy already paid in and not yet turned into Vis, in Q, stored beside (never inside)
     * TC4R's own wand state. Legacy name was {@code energy}/{@code tempEnergy}.
     */
    private static final String TAG_CHARGE = "technom_charge";

    private TechnomWandRods() {
    }

    public static Optional<WandView> view(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : WandApi.view(stack);
    }

    public static boolean hasRod(ItemStack stack, WandMaterialId rod) {
        return view(stack).map(v -> v.rod().equals(rod)).orElse(false);
    }

    public static long charge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_CHARGE, Tag.TAG_LONG) ? Math.max(0, tag.getLong(TAG_CHARGE)) : 0;
    }

    public static void setCharge(ItemStack stack, long q) {
        if (q <= 0) {
            if (stack.getTag() != null) {
                stack.removeTagKey(TAG_CHARGE);
            }
        } else {
            stack.getOrCreateTag().putLong(TAG_CHARGE, q);
        }
    }
}
