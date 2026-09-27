package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;

/**
 * Items that are not block items.
 *
 * <p>The 1.7.10 original packed the crafting materials into one damage-value item
 * ({@code ItemTHMaterial}, metas 0-3) because item ids were scarce. They are separate items
 * here; meta 4 of that item was a placeholder that rewrote itself into the coil coupler on
 * inventory tick, and the coil coupler was never registered, so neither is ported.</p>
 */
public final class TechnomItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Technomancy.MOD_ID);

    /** {@code itemMaterial:0}. */
    public static final RegistryObject<Item> NEUTRONIZED_METAL = simple("neutronized_metal");
    /** {@code itemMaterial:1}. */
    public static final RegistryObject<Item> ENCHANTED_COIL = simple("enchanted_coil");
    /** {@code itemMaterial:2}. */
    public static final RegistryObject<Item> NEUTRONIZED_GEAR = simple("neutronized_gear");
    /** {@code itemMaterial:3}. */
    public static final RegistryObject<Item> PEN_CORE = simple("pen_core");
    /** {@code itemBoost}: quadruples a dynamo's throughput, not its efficiency. */
    public static final RegistryObject<Item> POTENCY_GEM = simple("potency_gem");

    // ---- S2 machines and storage ----

    static {
        // One item per material and purity stage; see TechnomPureOres for why the set is fixed.
        TechnomPureOres.register(ITEMS);
    }

    private TechnomItems() {
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
