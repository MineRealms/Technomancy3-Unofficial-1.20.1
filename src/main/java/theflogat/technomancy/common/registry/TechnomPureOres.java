package theflogat.technomancy.common.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.common.items.PureOreItem;
import theflogat.technomancy.common.machines.processing.OreProgress;
import theflogat.technomancy.common.machines.processing.PureOreMaterial;

/**
 * The purified ores: {@link PureOreMaterial} x {@link OreProgress#STAGES} items.
 *
 * <p>The original registered these dynamically, one item per material found in the
 * {@code OreDictionary} at startup ({@code TMItems.initPureOres}), which made the item ids depend
 * on the mod list. The set is fixed here instead.</p>
 */
public final class TechnomPureOres {

    private static final Map<PureOreMaterial, List<RegistryObject<Item>>> ITEMS =
            new EnumMap<>(PureOreMaterial.class);

    private TechnomPureOres() {
    }

    /** Called once from {@link TechnomItems}'s initialiser. */
    static void register(DeferredRegister<Item> items) {
        if (!ITEMS.isEmpty()) {
            throw new IllegalStateException("the purified ores are already registered");
        }
        for (PureOreMaterial material : PureOreMaterial.values()) {
            List<RegistryObject<Item>> stages = new ArrayList<>(OreProgress.STAGES);
            for (int stage = 0; stage < OreProgress.STAGES; stage++) {
                int current = stage;
                stages.add(items.register(material.itemName(stage),
                        () -> new PureOreItem(material, current, new Item.Properties())));
            }
            ITEMS.put(material, List.copyOf(stages));
        }
    }

    /** The purified item of one material and stage. */
    public static Item item(PureOreMaterial material, int stage) {
        ensureInitialised();
        List<RegistryObject<Item>> stages = ITEMS.get(material);
        if (stages == null || stage < 0 || stage >= stages.size()) {
            throw new IllegalArgumentException("no purified " + material + " at stage " + stage);
        }
        return stages.get(stage).get();
    }

    /** Every purified ore item, for the client's tint handler. */
    public static List<Item> all() {
        ensureInitialised();
        List<Item> all = new ArrayList<>();
        ITEMS.values().forEach(stages -> stages.forEach(item -> all.add(item.get())));
        return all;
    }

    /**
     * {@link #register} runs from {@link TechnomItems}'s initialiser, so a caller that reached
     * this class first would otherwise see an empty map.
     */
    private static void ensureInitialised() {
        java.util.Objects.requireNonNull(TechnomItems.ITEMS, "item registry");
    }
}
