package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.menu.ProcessorMenu;

/** Menu types. Registered from the mod constructor, like the other registries. */
public final class TechnomMenus {

    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(Registries.MENU, Technomancy.MOD_ID);

    // ---- S2 machines and storage ----

    /** Shared by every ore processor; the machine decides what the fuel gauge means. */
    public static final RegistryObject<MenuType<ProcessorMenu>> PROCESSOR = TYPES.register("processor",
            () -> IForgeMenuType.create((id, inventory, data) -> ProcessorMenu.client(id, inventory)));

    private TechnomMenus() {
    }
}
