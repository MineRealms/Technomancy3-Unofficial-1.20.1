package theflogat.technomancy.common.items;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

/**
 * {@code ItemManaBucket}: the mana fluid's bucket. The original drew the vanilla water bucket icon,
 * which is kept, but it is a real bucket now instead of a plain {@code ItemBucket}.
 */
public final class ManaBucketItem extends BucketItem {

    public ManaBucketItem(Supplier<? extends Fluid> fluid) {
        super(fluid, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidBucketWrapper(stack);
    }
}
