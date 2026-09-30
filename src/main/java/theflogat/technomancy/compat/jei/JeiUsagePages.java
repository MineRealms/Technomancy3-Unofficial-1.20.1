package theflogat.technomancy.compat.jei;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomItems;

/**
 * The items and blocks that get a JEI "usage" page: the ones whose behaviour a player cannot
 * work out from the recipe alone.
 *
 * <p>Everything this mod adds is already visible in JEI as a recipe - the crafting, smelting and
 * shapeless recipes are ordinary data-pack recipes, and the essentia fuel table has a category of
 * its own. What JEI cannot derive is <em>what a machine does with what it is given</em>: that a
 * dynamo burns aspects rather than items, that an exchanger only talks to the pool directly above
 * it, that a coil is bound with a separate tool. Those are the pages below.</p>
 *
 * <p>Deliberately not in the list: {@code fake_air_light} and {@code node_fabricator_shell},
 * which are invisible scaffolding with no item at all; {@code basalt}, which is an ordinary
 * decorative rock; and the plain crafting materials ({@code technoturge_core},
 * {@code mana_coil}, {@code manasteel_gear}), whose only fact is their recipe.</p>
 *
 * <p>The list lives here rather than in {@link TechnomJeiPlugin} because this class must stay
 * free of JEI types: {@link theflogat.technomancy.compat.jei} is only on the compile and runtime
 * classpaths, never on the test one, so a JUnit guard cannot load a class that implements
 * {@code IModPlugin}. The guard reads {@link #PAGES} and checks every {@link Page#path()} has a
 * translation in every language file - a missing one renders in game as the raw key, which no
 * compiler catches.</p>
 *
 * <p>The Botania pages are listed unconditionally and filtered at registration time. Their
 * suppliers read {@link theflogat.technomancy.compat.botania.BotaniaContent}'s fields, which stay
 * null when Botania is absent, so they must never be invoked in that case.</p>
 */
public final class JeiUsagePages {

    /** {@code technom.jei.info.} + a page's path is its translation key. */
    public static final String PREFIX = "technom.jei.info.";

    /**
     * One usage page.
     *
     * @param item    the ingredient the page hangs off. A {@link Supplier} because a registry
     *                object's value does not exist until registration has run.
     * @param path    the translation key's suffix; see {@link #PREFIX}
     * @param botania whether this item is only registered when Botania is loaded
     */
    public record Page(Supplier<? extends ItemLike> item, String path, boolean botania) {

        static Page of(Supplier<? extends ItemLike> item, String path) {
            return new Page(item, path, false);
        }

        static Page botania(Supplier<? extends ItemLike> item, String path) {
            return new Page(item, path, true);
        }
    }

    /**
     * Every usage page, in the order a player is likely to meet them. Several entries may share a
     * path: the five crystals differ only in colour and the three pylons only in throughput, so
     * they share one page each.
     */
    public static final List<Page> PAGES = List.of(
            // ---- Energy and essentia ----
            Page.of(TechnomBlocks.QUANTIZED_GLASS, "quantized_glass"),
            Page.of(TechnomBlocks.QUANTUM_JAR, "quantum_jar"),
            Page.of(TechnomBlocks.ENERGY_CONDENSER, "energy_condenser"),
            Page.of(TechnomBlocks.ESSENTIA_DYNAMO, "essentia_dynamo"),
            Page.of(TechnomBlocks.ITEM_COIL, "item_coil"),
            Page.of(TechnomBlocks.ESSENTIA_COIL, "essentia_coil"),
            Page.of(TechnomItems.COIL_COUPLER, "coil_coupler"),
            Page.of(TechnomBlocks.NODE_DYNAMO, "node_dynamo"),
            Page.of(TechnomBlocks.NODE_FABRICATOR, "node_fabricator"),
            Page.of(TechnomItems.FUSION_FOCUS, "fusion_focus"),
            Page.of(TechnomBlocks.ESSENTIA_RESERVOIR, "essentia_reservoir"),
            Page.of(TechnomBlocks.CREATIVE_JAR, "creative_jar"),
            Page.of(TechnomBlocks.PROCESSOR_TC, "processor_tc"),
            Page.of(TechnomBlocks.ESSENTIA_FUSOR, "essentia_fusor"),
            Page.of(TechnomBlocks.ADV_DECON_TABLE, "adv_decon_table"),
            Page.of(TechnomBlocks.ELDRITCH_CONSUMER, "eldritch_consumer"),
            Page.of(TechnomBlocks.FLUX_LAMP, "flux_lamp"),
            Page.of(TechnomBlocks.ELECTRIC_BELLOWS, "electric_bellows"),
            Page.of(TechnomBlocks.BIOME_MORPHER, "biome_morpher"),

            // ---- Rituals ----
            Page.of(TechnomBlocks.CRYSTAL_EARTH, "ritual_crystal"),
            Page.of(TechnomBlocks.CRYSTAL_FIRE, "ritual_crystal"),
            Page.of(TechnomBlocks.CRYSTAL_WATER, "ritual_crystal"),
            Page.of(TechnomBlocks.CRYSTAL_LIGHT, "ritual_crystal"),
            Page.of(TechnomBlocks.CRYSTAL_DARK, "ritual_crystal"),
            Page.of(TechnomBlocks.CATALYST_EARTH, "ritual_catalyst"),
            Page.of(TechnomBlocks.CATALYST_FIRE, "ritual_catalyst"),
            Page.of(TechnomBlocks.CATALYST_WATER, "ritual_catalyst"),
            Page.of(TechnomBlocks.CATALYST_LIGHT, "ritual_catalyst"),
            Page.of(TechnomBlocks.CATALYST_DARK, "ritual_catalyst"),
            Page.of(TechnomItems.RITUAL_TOME, "ritual_tome"),
            Page.of(TechnomItems.PEN, "pen"),

            // ---- Existence ----
            Page.of(TechnomBlocks.EXISTENCE_BURNER, "existence_burner"),
            Page.of(TechnomBlocks.EXISTENCE_DYNAMIC_BURNER, "existence_dynamic_burner"),
            Page.of(TechnomItems.EXISTENCE_GEM, "existence_gem"),
            Page.of(TechnomBlocks.EXISTENCE_PYLON_BASIC, "existence_pylon"),
            Page.of(TechnomBlocks.EXISTENCE_PYLON_ADVANCED, "existence_pylon"),
            Page.of(TechnomBlocks.EXISTENCE_PYLON_COMPLEX, "existence_pylon"),
            Page.of(TechnomBlocks.EXISTENCE_CROP_ACCELERATOR, "existence_crop_accelerator"),
            Page.of(TechnomBlocks.EXISTENCE_HARVESTER, "existence_harvester"),
            Page.of(TechnomBlocks.EXISTENCE_SEALER, "existence_sealer"),
            Page.of(TechnomBlocks.EXISTENCE_FOUNTAIN, "existence_fountain"),
            Page.of(TechnomItems.TREASURE_FIRE_GEM, "treasure_fire_gem"),
            Page.of(TechnomItems.TREASURE_POWER_PLATE, "treasure_power_plate"),
            Page.of(TechnomItems.TREASURE_GOLDEN_WING, "treasure_golden_wing"),

            // ---- Botania, only when it is installed ----
            Page.botania(theflogat.technomancy.compat.botania.BotaniaContent.FLOWER_DYNAMO, "flower_dynamo"),
            Page.botania(theflogat.technomancy.compat.botania.BotaniaContent.MANA_FABRICATOR, "mana_fabricator"),
            Page.botania(theflogat.technomancy.compat.botania.BotaniaContent.MANA_EXCHANGER, "mana_exchanger"),
            Page.botania(theflogat.technomancy.compat.botania.BotaniaContent.PROCESSOR_BO, "processor_bo"));

    /** The distinct translation keys, in list order; what the lang guard iterates. */
    public static List<String> paths() {
        return PAGES.stream().map(Page::path).distinct().toList();
    }

    private JeiUsagePages() {
    }
}
