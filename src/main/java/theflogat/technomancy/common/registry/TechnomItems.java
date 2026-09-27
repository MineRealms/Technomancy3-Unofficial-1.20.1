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

    // ---- S2 coils ----
    /** {@code coilCoupler}: links a coil to the blocks it draws from. */
    public static final RegistryObject<Item> COIL_COUPLER = ITEMS.register("coil_coupler",
            () -> new theflogat.technomancy.common.items.coils.CoilCouplerItem(new Item.Properties().stacksTo(1)));
    // ---- end S2 coils ----

    // ---- S2 nodes, wands and fusion ----

    /**
     * {@code itemWandCores:0}, the energized wand rod. Its wand-part definition is data
     * ({@code thaumcraft:wand_rods} data map + tag); see
     * {@link theflogat.technomancy.common.wands.TechnomWandRods}.
     */
    public static final RegistryObject<Item> ENERGIZED_WAND_CORE = simple("energized_wand_core");
    /**
     * {@code itemWandCores:1}, the technoturge sceptre rod. Glinted, as {@code hasEffect} was for
     * metadata 1; both cores shared one icon in 1.7.10 and still do.
     */
    public static final RegistryObject<Item> TECHNOTURGE_CORE = ITEMS.register("technoturge_core",
            () -> new Item(new Item.Properties()) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                    return true;
                }
            });

    /** {@code itemPen}: a 3000-use scribing tool that also forms the research table. */
    public static final RegistryObject<Item> PEN = ITEMS.register("pen",
            () -> new theflogat.technomancy.common.items.PenItem(new Item.Properties()));

    /** {@code itemFusionFocus}: moves one aura node's vis into another. */
    public static final RegistryObject<Item> FUSION_FOCUS = ITEMS.register("fusion_focus",
            () -> new theflogat.technomancy.common.items.FusionFocusItem(new Item.Properties()));

    // ---- end S2 nodes, wands and fusion ----

    private TechnomItems() {
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
