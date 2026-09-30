package theflogat.technomancy.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.registry.TechnomBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The eight tabs of the ritual tome. Each tab is a {@link Tab} with a name, an icon index into
 * {@code textures/gui/tomeButtonsRitual.png} (16×16 strips laid out top-to-bottom), and a list
 * of {@link Entry Entries}; each entry is a label plus a list of page spreads — every spread
 * is exactly two {@link RitualTomePage halves}.
 *
 * <p>Every string here is a translation key rather than the text itself. The keys live under
 * {@code technom.tome.*} in {@code assets/technom/lang/}: {@code tab.<tab>} for a tab's name,
 * {@code entry.<tab>.<entry>} for a chapter label, and {@code page.<tab>.<entry>.<page>} for a
 * page's body. A page's value may contain {@code \n} to force a paragraph break; anything longer
 * than the half-page is wrapped by {@link RitualTomePage#wrap}. {@link #build()} is called once
 * each time the screen opens, so a language change takes effect the next time the book is used.</p>
 */
public final class RitualTomeContent {

    /** A single tab: name, icon index, and entries. */
    public static final class Tab {
        public final Component name;
        public final int iconIndex;
        public final List<Entry> entries = new ArrayList<>();
        public Tab(String nameKey, int iconIndex) {
            this.name = Component.translatable(nameKey);
            this.iconIndex = iconIndex;
        }
    }

    /** One labelled entry in a tab; its {@code spreads} are two-page pairs in render order. */
    public static final class Entry {
        public final Component label;
        public final List<RitualTomePage[]> spreads = new ArrayList<>();
        public Entry(String labelKey) {
            this.label = Component.translatable(labelKey);
        }
        public Entry add(RitualTomePage left, RitualTomePage right) {
            spreads.add(new RitualTomePage[] { left, right });
            return this;
        }
    }

    private RitualTomeContent() {}

    public static final ResourceLocation BACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "textures/gui/tomebackritual.png");
    public static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "textures/gui/tomebuttonsritual.png");
    public static final ResourceLocation NEXT_PREV = ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "textures/gui/nextprevious.png");

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "textures/gui/" + name);
    }

    /** {@code technom.tome.page.} + the page's path, so the key scheme stays in one place. */
    private static String page(String path) {
        return "technom.tome.page." + path;
    }

    private static String entry(String path) {
        return "technom.tome.entry." + path;
    }

    /** Build the eight tabs. Called once when the screen opens. */
    public static List<Tab> build() {
        List<Tab> tabs = new ArrayList<>();

        // ---- 0. Ritual 101 ----
        Tab t101 = new Tab("technom.tome.tab.ritual101", 0);
        t101.entries.add(new Entry(entry("ritual101.introduction"))
                .add(
                        RitualTomePage.text(false, page("ritual101.introduction.left")),
                        RitualTomePage.text(true, page("ritual101.introduction.right"))));
        t101.entries.add(new Entry(entry("ritual101.crystals"))
                .add(
                        RitualTomePage.text(false, page("ritual101.crystals.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.CRYSTAL_EARTH.get()))));
        t101.entries.add(new Entry(entry("ritual101.catalysts"))
                .add(
                        RitualTomePage.text(false, page("ritual101.catalysts.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.CATALYST_EARTH.get()))));
        tabs.add(t101);

        // ---- 1. Dark Rituals (Black Hole T1-T3) ----
        Tab dark = new Tab("technom.tome.tab.dark", 1);
        Entry bh = new Entry(entry("dark.black_hole"));
        bh.add(
                RitualTomePage.text(false, page("dark.black_hole.t1")),
                RitualTomePage.image(true, gui("blackholet1.png"), 54, 0));
        bh.add(
                RitualTomePage.text(false, page("dark.black_hole.t2")),
                RitualTomePage.image(true, gui("blackholet2.png"), 54, 0));
        bh.add(
                RitualTomePage.text(false, page("dark.black_hole.t3")),
                RitualTomePage.image(true, gui("blackholet3.png"), 54, 0));
        dark.entries.add(bh);
        tabs.add(dark);

        // ---- 2. Light Rituals (Purification T1-T3) ----
        Tab light = new Tab("technom.tome.tab.light", 2);
        Entry pu = new Entry(entry("light.purification"));
        pu.add(
                RitualTomePage.text(false, page("light.purification.t1")),
                RitualTomePage.image(true, gui("purificationt1.png"), 54, 0));
        pu.add(
                RitualTomePage.text(false, page("light.purification.t2")),
                RitualTomePage.image(true, gui("purificationt2.png"), 54, 0));
        pu.add(
                RitualTomePage.text(false, page("light.purification.t3")),
                RitualTomePage.image(true, gui("purificationt3.png"), 54, 0));
        light.entries.add(pu);
        tabs.add(light);

        // ---- 3. Fire Rituals (Fire T1-T3) ----
        Tab fire = new Tab("technom.tome.tab.fire", 3);
        Entry ft = new Entry(entry("fire.fire"));
        ft.add(
                RitualTomePage.text(false, page("fire.fire.t1")),
                RitualTomePage.image(true, gui("firet1.png"), 54, 0));
        ft.add(
                RitualTomePage.text(false, page("fire.fire.t2")),
                RitualTomePage.image(true, gui("firet2.png"), 54, 0));
        ft.add(
                RitualTomePage.text(false, page("fire.fire.t3")),
                RitualTomePage.image(true, gui("firet3.png"), 54, 0));
        fire.entries.add(ft);
        tabs.add(fire);

        // ---- 4. Water Rituals (Water T1-T3) ----
        Tab water = new Tab("technom.tome.tab.water", 4);
        Entry wt = new Entry(entry("water.water"));
        wt.add(
                RitualTomePage.text(false, page("water.water.t1")),
                RitualTomePage.image(true, gui("watert1.png"), 54, 0));
        wt.add(
                RitualTomePage.text(false, page("water.water.t2")),
                RitualTomePage.image(true, gui("watert2.png"), 54, 0));
        wt.add(
                RitualTomePage.text(false, page("water.water.t3")),
                RitualTomePage.image(true, gui("watert3.png"), 54, 0));
        water.entries.add(wt);
        tabs.add(water);

        // ---- 5. Earth Rituals (Cave-In / Collapse T1-T3) ----
        Tab earth = new Tab("technom.tome.tab.earth", 5);
        Entry ct = new Entry(entry("earth.collapse"));
        ct.add(
                RitualTomePage.text(false, page("earth.collapse.t1")),
                RitualTomePage.image(true, gui("caveint1.png"), 54, 0));
        ct.add(
                RitualTomePage.text(false, page("earth.collapse.t2")),
                RitualTomePage.image(true, gui("caveint2.png"), 54, 0));
        ct.add(
                RitualTomePage.text(false, page("earth.collapse.t3")),
                RitualTomePage.image(true, gui("caveint3.png"), 54, 0));
        earth.entries.add(ct);
        tabs.add(earth);

        // ---- 6. Power of Existence ----
        Tab poe = new Tab("technom.tome.tab.existence", 6);
        poe.entries.add(new Entry(entry("existence.introduction"))
                .add(
                        RitualTomePage.text(false, page("existence.introduction.left")),
                        RitualTomePage.text(true, page("existence.introduction.right"))));
        poe.entries.add(new Entry(entry("existence.farming"))
                .add(
                        RitualTomePage.text(false, page("existence.farming.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_BURNER.get()))));
        poe.entries.add(new Entry(entry("existence.fountain"))
                .add(
                        RitualTomePage.text(false, page("existence.fountain.left")),
                        RitualTomePage.image(true, gui("fountain.png"), -64, 0)));
        poe.entries.add(new Entry(entry("existence.pylons"))
                .add(
                        RitualTomePage.text(false, page("existence.pylons.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_PYLON_BASIC.get()))));
        poe.entries.add(new Entry(entry("existence.better_farming"))
                .add(
                        RitualTomePage.text(false, page("existence.better_farming.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_CROP_ACCELERATOR.get()))));
        tabs.add(poe);

        // ---- 7. Treasures ----
        Tab treasures = new Tab("technom.tome.tab.treasures", 7);
        treasures.entries.add(new Entry(entry("treasures.introduction"))
                .add(
                        RitualTomePage.text(false, page("treasures.introduction.left")),
                        RitualTomePage.text(true, page("treasures.introduction.right"))));
        treasures.entries.add(new Entry(entry("treasures.extraction"))
                .add(
                        RitualTomePage.text(false, page("treasures.extraction.left")),
                        RitualTomePage.image(true, gui("extract.png"), 54, 0)));
        treasures.entries.add(new Entry(entry("treasures.protection"))
                .add(
                        RitualTomePage.text(false, page("treasures.protection.left")),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_SEALER.get()))));
        tabs.add(treasures);

        return tabs;
    }
}
