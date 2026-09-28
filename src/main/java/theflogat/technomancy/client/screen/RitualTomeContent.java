package theflogat.technomancy.client.screen;

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
 */
public final class RitualTomeContent {

    /** A single tab: name, icon index, and entries. */
    public static final class Tab {
        public final String name;
        public final int iconIndex;
        public final List<Entry> entries = new ArrayList<>();
        public Tab(String name, int iconIndex) { this.name = name; this.iconIndex = iconIndex; }
    }

    /** One labelled entry in a tab; its {@code spreads} are two-page pairs in render order. */
    public static final class Entry {
        public final String label;
        public final List<RitualTomePage[]> spreads = new ArrayList<>();
        public Entry(String label) { this.label = label; }
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

    /** Build the eight tabs. Called once when the screen opens. */
    public static List<Tab> build() {
        List<Tab> tabs = new ArrayList<>();

        // ---- 0. Ritual 101 ----
        Tab t101 = new Tab("Ritual 101", 0);
        t101.entries.add(new Entry("Introduction")
                .add(
                        RitualTomePage.text(false,
                                "Rituals are weak summons of nature's power. They are easy to set up and don't cost too much in resources. Read on for how to make and use them."),
                        RitualTomePage.text(true,
                                "Crystals form the frame, and the catalyst is the core. Place them as described in the following pages and trigger the catalyst by right-clicking it or with redstone.")));
        t101.entries.add(new Entry("Crystal Blocks")
                .add(
                        RitualTomePage.text(false,
                                "Crystals are what you use for the frame of the rituals. They contain enough power to emit light. Crystals have a natural ability to densify when placed above another one."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.CRYSTAL_EARTH.get()))));
        t101.entries.add(new Entry("Catalysts")
                .add(
                        RitualTomePage.text(false,
                                "Catalysts are the core of the rituals. They determine the main element which the ritual will lean towards. They can be activated either by being right-clicked or by receiving a redstone signal."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.CATALYST_EARTH.get()))));
        tabs.add(t101);

        // ---- 1. Dark Rituals (Black Hole T1-T3) ----
        Tab dark = new Tab("Dark Rituals", 1);
        Entry bh = new Entry("Black Hole");
        bh.add(
                RitualTomePage.text(false, lines("Tier 1:", "Will destroy every block and kill every living thing in a 7×7×7 area.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("blackholet1.png"), 54, 0));
        bh.add(
                RitualTomePage.text(false, lines("Tier 2:", "Will destroy every block and kill every living thing in an 11×11×11 area.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("blackholet2.png"), 54, 0));
        bh.add(
                RitualTomePage.text(false, lines("Tier 3:", "Will destroy every block and kill every living thing in a 19×19×19 area.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("blackholet3.png"), 54, 0));
        dark.entries.add(bh);
        tabs.add(dark);

        // ---- 2. Light Rituals (Purification T1-T3) ----
        Tab light = new Tab("Light Rituals", 2);
        Entry pu = new Entry("Purification");
        pu.add(
                RitualTomePage.text(false, lines("Tier 1:", "Kills every monster above the catalyst in a 1×1×3 column.")),
                RitualTomePage.image(true, gui("purificationt1.png"), 54, 0));
        pu.add(
                RitualTomePage.text(false, lines("Tier 2:", "Kills every monster above the catalyst in a 3×3×11 column.")),
                RitualTomePage.image(true, gui("purificationt2.png"), 54, 0));
        pu.add(
                RitualTomePage.text(false, lines("Tier 3:", "Kills every monster above the catalyst in a 7×7 column.", "Range: world max height.")),
                RitualTomePage.image(true, gui("purificationt3.png"), 54, 0));
        light.entries.add(pu);
        tabs.add(light);

        // ---- 3. Fire Rituals (Fire T1-T3) ----
        Tab fire = new Tab("Fire Rituals", 3);
        Entry ft = new Entry("Fire");
        ft.add(
                RitualTomePage.text(false, lines("Tier 1:", "Converts water to obsidian in a 19×19×19 area under the ritual.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("firet1.png"), 54, 0));
        ft.add(
                RitualTomePage.text(false, lines("Tier 2:", "Creates a 9×9×19 lava pool.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("firet2.png"), 54, 0));
        ft.add(
                RitualTomePage.text(false, lines("Tier 3: DISABLED.", "Would make a volcano emerge.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("firet3.png"), 54, 0));
        fire.entries.add(ft);
        tabs.add(fire);

        // ---- 4. Water Rituals (Water T1-T3) ----
        Tab water = new Tab("Water Rituals", 4);
        Entry wt = new Entry("Water");
        wt.add(
                RitualTomePage.text(false, lines("Tier 1:", "Places water underneath in a 3×3 area.")),
                RitualTomePage.image(true, gui("watert1.png"), 54, 0));
        wt.add(
                RitualTomePage.text(false, lines("Tier 2:", "Places water underneath in a 7×7 area.")),
                RitualTomePage.image(true, gui("watert2.png"), 54, 0));
        wt.add(
                RitualTomePage.text(false, lines("Tier 3:", "Replaces blocks underneath with water in a 19×19 area.", "Ritual is consumed.")),
                RitualTomePage.image(true, gui("watert3.png"), 54, 0));
        water.entries.add(wt);
        tabs.add(water);

        // ---- 5. Earth Rituals (Cave-In / Collapse T1-T3) ----
        Tab earth = new Tab("Earth Rituals", 5);
        Entry ct = new Entry("Collapse");
        ct.add(
                RitualTomePage.text(false, lines("Tier 1:", "Closes all the gaps underneath the ritual in a 3×3 area.", "Good for closing caves. Ritual is consumed.")),
                RitualTomePage.image(true, gui("caveint1.png"), 54, 0));
        ct.add(
                RitualTomePage.text(false, lines("Tier 2:", "Closes all the gaps underneath the ritual in a 7×7 area.", "Good for closing caves. Ritual is consumed.")),
                RitualTomePage.image(true, gui("caveint2.png"), 54, 0));
        ct.add(
                RitualTomePage.text(false, lines("Tier 3:", "Closes all the gaps underneath the ritual in an 11×11 area.", "Good for closing caves. Ritual is consumed.")),
                RitualTomePage.image(true, gui("caveint3.png"), 54, 0));
        earth.entries.add(ct);
        tabs.add(earth);

        // ---- 6. Power of Existence ----
        Tab poe = new Tab("Power of Existence", 6);
        poe.entries.add(new Entry("Introduction")
                .add(
                        RitualTomePage.text(false,
                                "Every living being comes from a single source: Power of Existence. This power is the very root of any existence. A small amount is present in each being, and when a large amount is focused in one place it becomes rather dangerous — there exist ways to prevent instability."),
                        RitualTomePage.text(true,
                                "You have discovered that emeralds are a particularly great catalyst. It seems this gem stores a very high amount of power. You decide to experiment and manage to create a gem that can store a small amount of existence.")));
        poe.entries.add(new Entry("Farming Power")
                .add(
                        RitualTomePage.text(false,
                                "You have found a way to collect a meek amount of existence and store it in a single device. The power is collected from nearby creatures. Unfortunately, doing so kills them — the smarter the creature, the more power it has."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_BURNER.get()))));
        poe.entries.add(new Entry("Birth of the Fountain")
                .add(
                        RitualTomePage.text(false,
                                "The Birth of the Fountain is a ritual that converts the existence of nearby beings into a self-sustaining mass. It takes the shape of a cobblestone Graal — this shape is supposedly the best for holding nonsense."),
                        RitualTomePage.image(true, gui("fountain.png"), -64, 0)));
        poe.entries.add(new Entry("Pylons")
                .add(
                        RitualTomePage.text(false,
                                "Using emeralds has proven to be a good way to allow the transfer of Power. The Pylons have an inherent ability to draw power where it is highly focused and to disperse it where it is not."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_PYLON_BASIC.get()))));
        poe.entries.add(new Entry("Better Farming")
                .add(
                        RitualTomePage.text(false,
                                "It seems crops react in an interesting way to Power of Existence. This device uses the power to accelerate crop growth in a 9×9 area. It must be placed underneath the soil."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_CROP_ACCELERATOR.get()))));
        tabs.add(poe);

        // ---- 7. Treasures ----
        Tab treasures = new Tab("Treasures", 7);
        treasures.entries.add(new Entry("Treasures?!?")
                .add(
                        RitualTomePage.text(false,
                                "Some lore you found in a village library seems to indicate that some villagers have shown otherworldly characteristics. They look like normal villagers but exhibit incredible capacities."),
                        RitualTomePage.text(true,
                                "You found a special note indicating that the power comes from some artifacts buried inside the existence mass of villagers. You think of a way to extract them — and you conclude rituals are your best ally.")));
        treasures.entries.add(new Entry("Extraction")
                .add(
                        RitualTomePage.text(false,
                                "The Ritual of Extraction serves to extract artifacts from special villagers. It is quite unstable and may cause catastrophes if not used correctly."),
                        RitualTomePage.image(true, gui("extract.png"), 54, 0)));
        treasures.entries.add(new Entry("Protection")
                .add(
                        RitualTomePage.text(false,
                                "Now that you know how dangerous those villagers are, you decide to invent a device that seals their power temporarily. The Existence Sealing Device allows an easy way to seal off a nearby powerful villager for 4 seconds, at an important existence cost."),
                        RitualTomePage.recipe(true, new ItemStack(TechnomBlocks.EXISTENCE_SEALER.get()))));
        tabs.add(treasures);

        return tabs;
    }

    private static List<String> lines(String... ls) {
        return List.of(ls);
    }
}