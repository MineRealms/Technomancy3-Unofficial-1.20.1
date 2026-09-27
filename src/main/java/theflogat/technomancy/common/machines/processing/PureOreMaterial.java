package theflogat.technomancy.common.machines.processing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import theflogat.technomancy.Technomancy;

/**
 * A material the ore processors can purify.
 *
 * <p>The original discovered these at runtime by walking the {@code OreDictionary} for every
 * {@code ore*}/{@code ingot*} pair whose ore smelted to that ingot ({@code Ore.init},
 * {@code util/Ore.java:11-47}), then registered one item per material. Registry contents are
 * not a stable identity in 1.20.1 — the item ids would change with the mod list — so the set is
 * fixed here, and which items count as "one ore's worth" of it is a data-pack tag
 * ({@code technom:processable/<id>}), which is also how a modpack adds a modded ore.</p>
 *
 * <p>Only the three vanilla metals ship. Tin, silver, lead and nickel existed in the original
 * because other 1.7.10 mods provided the ingot; adding them here would need an ingot that may
 * not exist, and the purified ore would smelt to nothing. GregTech materials are the obvious
 * next source and are deliberately left to whoever wires up the GT chain, because the yields
 * have to be balanced against GT's own multipliers first.</p>
 *
 * <p>{@code tint} is applied to the grayscale {@code ore0..ore5} textures. The original averaged
 * the ingot texture's pixels at startup ({@code Ore.getColor}), which cannot work on a dedicated
 * server and so belonged to the client only; these are fixed values.</p>
 */
public enum PureOreMaterial {

    IRON("iron", 0xD8D8D8),
    GOLD("gold", 0xFAEE4D),
    COPPER("copper", 0xE0734D);

    private final String id;
    private final int tint;
    private final TagKey<Item> processable;

    PureOreMaterial(String id, int tint) {
        this.id = id;
        this.tint = tint;
        this.processable = ItemTags.create(new ResourceLocation(Technomancy.MOD_ID, "processable/" + id));
    }

    public String id() {
        return id;
    }

    public int tint() {
        return tint;
    }

    /** Items that are one ore's worth of this material: its raw drops and its ore blocks. */
    public TagKey<Item> processable() {
        return processable;
    }

    /** Registry name of the purified item at {@code stage}, e.g. {@code pure_iron_0}. */
    public String itemName(int stage) {
        return "pure_" + id + "_" + stage;
    }

    /** Lang key of the display name shared by every stage, e.g. {@code item.technom.pure_iron}. */
    public String translationKey() {
        return "item." + Technomancy.MOD_ID + ".pure_" + id;
    }
}
