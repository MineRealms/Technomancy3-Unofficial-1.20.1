package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;

/** The mod's creative tab, filled from the item registry so nothing can be left out of it. */
public final class TechnomCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Technomancy.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Technomancy.MOD_ID))
            .icon(() -> new ItemStack(TechnomBlocks.QUANTIZED_GLASS.get()))
            .displayItems((parameters, output) ->
                    TechnomItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private TechnomCreativeTabs() {
    }
}
