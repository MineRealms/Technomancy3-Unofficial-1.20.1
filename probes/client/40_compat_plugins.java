//@wait 2
// The three compat modules are the parts of this mod that a player only notices when they are
// missing: JEI shows no fuel page, Jade shows no numbers, KubeJS silently binds nothing. None of
// that fails a build, so it is checked here instead. KubeJS itself is not on the development
// runtime (it is compileOnly), so its half is checked through the discovery file and the class
// resource rather than by loading the class.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null) {
    return "FAIL: the client is not in a world\n";
}
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

// ---- Jade ----
if (!net.minecraftforge.fml.ModList.get().isLoaded("jade")) {
    bad.add("jade is not loaded, so its half of this probe proves nothing");
}
var provider = theflogat.technomancy.compat.jade.TechnomJadeProvider.INSTANCE;
out.append("jade provider uid = ").append(provider.getUid()).append("\n");
if (!provider.getUid().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("technom", "machine_data"))) {
    bad.add("the jade provider uid is " + provider.getUid());
}
// A tooltip line whose key is missing renders as the raw key, which is a visible bug that no
// compiler catches.
String[] jadeKeys = {
    "technom.jade.energy", "technom.jade.essentia", "technom.jade.progress", "technom.jade.vis",
    "technom.jade.buffer", "technom.jade.ritual", "technom.jade.remaining", "technom.jade.existence",
    "technom.jade.element.earth", "technom.jade.element.fire", "technom.jade.element.water",
    "technom.jade.element.light", "technom.jade.element.dark",
};
int missingJade = 0;
for (String key : jadeKeys) {
    if (!net.minecraft.locale.Language.getInstance().has(key)) {
        bad.add("missing lang key " + key);
        missingJade++;
    }
}
out.append("jade lang keys present = ").append(jadeKeys.length - missingJade).append("/").append(jadeKeys.length).append("\n");

// Every block entity the provider is registered for must still exist as a class; a rename would
// leave Jade calling a provider that no longer matches anything.
java.lang.reflect.Field provided = theflogat.technomancy.compat.jade.TechnomJadePlugin.class
        .getDeclaredField("PROVIDED");
provided.setAccessible(true);
java.util.List<?> types = (java.util.List<?>) provided.get(null);
out.append("jade registered block entities = ").append(types.size()).append("\n");
if (types.size() < 15) {
    bad.add("jade only knows " + types.size() + " block entities");
}

// ---- JEI ----
if (!net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
    bad.add("jei is not loaded, so its half of this probe proves nothing");
}
Class<?> jeiPlugin = Class.forName("theflogat.technomancy.compat.jei.TechnomJeiPlugin");
if (!mezz.jei.api.IModPlugin.class.isAssignableFrom(jeiPlugin)) {
    bad.add("the jei plugin does not implement IModPlugin");
}
// @JeiPlugin declares no @Retention in JEI, so it is CLASS-retention and reflection can never
// see it - isAnnotationPresent is false whatever the class file actually says, which is a
// guaranteed false alarm. What matters is that JEI's annotation scan picked the plugin up, so
// ask JEI's own runtime instead. Internal is JEI's internal accessor; a probe may use it.
var recipeType = theflogat.technomancy.compat.jei.EssentiaFuelCategory.RECIPE_TYPE;
out.append("jei recipe type = ").append(recipeType.getUid()).append("\n");
if (!recipeType.getUid().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("technom", "essentia_fuel"))) {
    bad.add("the jei recipe type uid is " + recipeType.getUid());
}
long jeiRegistered = -1L;
java.util.Optional<mezz.jei.api.runtime.IJeiRuntime> jeiRuntime =
        mezz.jei.common.Internal.getOptionalJeiRuntime();
if (jeiRuntime.isEmpty()) {
    bad.add("JEI is loaded but exposes no runtime, so its plugin scan cannot be checked");
} else {
    mezz.jei.api.recipe.IRecipeManager recipeManager = jeiRuntime.get().getRecipeManager();
    boolean known = recipeManager.getRecipeType(recipeType.getUid()).isPresent();
    out.append("jei knows the category = ").append(known).append("\n");
    if (!known) {
        bad.add("JEI never registered " + recipeType.getUid() + ", so the plugin was not discovered");
    } else {
        jeiRegistered = recipeManager.createRecipeLookup(recipeType).get().count();
        out.append("jei recipes registered = ").append(jeiRegistered).append("\n");
    }
}

// The pages the plugin will hand JEI, built from the table the data pack just loaded.
var table = theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader.table();
var pages = theflogat.technomancy.compat.jei.EssentiaFuelRecipe.all(table);
out.append("fuel table: ").append(table.listedAspects().size()).append(" aspects, fallback ")
        .append(table.fallback()).append(", ").append(pages.size()).append(" jei pages\n");
if (table.isEmpty()) {
    bad.add("the essentia fuel table is empty, so the jei category would be blank");
}
if (pages.size() != table.listedAspects().size() + 1) {
    bad.add("expected one page per aspect plus the fallback, got " + pages.size());
}
// What JEI actually received has to be what the plugin built, or the two drifted apart.
if (jeiRegistered >= 0 && jeiRegistered != pages.size()) {
    bad.add("JEI holds " + jeiRegistered + " essentia fuel recipes but the plugin built " + pages.size());
}
// The last page is the fallback: no aspect, no conditions, the table's own fallback value.
var last = pages.get(pages.size() - 1);
if (last.aspect() != null || !last.conditions().isEmpty() || last.value() != table.fallback()) {
    bad.add("the last page is not the fallback row");
}
// A named page must carry its row's own numbers, not the fallback's.
int checked = 0;
for (var page : pages) {
    if (page.aspect() == null) {
        continue;
    }
    var entry = table.entryFor(page.aspect());
    if (entry == null || page.value() != entry.value()
            || !page.conditions().equals(entry.conditions())
            || page.randomBonus() != entry.randomBonus()) {
        bad.add("the page for " + page.aspect().serialized() + " does not match its table row");
    }
    checked++;
}
out.append("fuel pages matching their row = ").append(checked).append("\n");
if (checked == 0) {
    bad.add("no named fuel page was checked");
}

String[] jeiKeys = {
    "technom.jei.essentia_fuel", "technom.jei.fuel.base", "technom.jei.fuel.when",
    "technom.jei.fuel.random", "technom.jei.fuel.any", "technom.jei.condition.biome",
    "technom.jei.condition.dimension", "technom.jei.condition.above", "technom.jei.condition.below",
    "technom.jei.condition.from_sea_level", "technom.jei.condition.and",
    "technom.jei.condition.slime", "technom.jei.condition.day", "technom.jei.condition.night",
};
for (String key : jeiKeys) {
    if (!net.minecraft.locale.Language.getInstance().has(key)) {
        bad.add("missing lang key " + key);
    }
}

// The usage pages. JEI puts these in its own built-in Information category, so "did the plugin
// run" is not the question - the question is whether JEI can find a page by item, which is what
// the player does with the U key. A page whose translation key is missing renders as the key
// itself, in a GUI no other test opens.
boolean botaniaLoaded = net.minecraftforge.fml.ModList.get().isLoaded("botania");
java.util.Set<String> withUsagePage = new java.util.HashSet<String>();
java.util.List<String> rawInfoKeys = new java.util.ArrayList<String>();
if (jeiRuntime.isPresent()) {
    java.util.Iterator<mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe> infoPages =
            jeiRuntime.get().getRecipeManager()
                    .createRecipeLookup(mezz.jei.api.constants.RecipeTypes.INFORMATION).get()
                    .iterator();
    while (infoPages.hasNext()) {
        mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe infoPage = infoPages.next();
        for (mezz.jei.api.ingredients.ITypedIngredient<?> ingredient : infoPage.getIngredients()) {
            Object value = ingredient.getIngredient();
            if (value instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                withUsagePage.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString());
            }
        }
        for (net.minecraft.network.chat.FormattedText line : infoPage.getDescription()) {
            String rendered = line.getString();
            if (rendered.startsWith(theflogat.technomancy.compat.jei.JeiUsagePages.PREFIX)) {
                rawInfoKeys.add(rendered);
            }
        }
    }
}
out.append("jei info recipes cover ").append(withUsagePage.size()).append(" item(s)\n");
int pagesChecked = 0;
int pagesMissing = 0;
for (theflogat.technomancy.compat.jei.JeiUsagePages.Page usage :
        theflogat.technomancy.compat.jei.JeiUsagePages.PAGES) {
    if (usage.botania() && !botaniaLoaded) {
        continue;
    }
    String usageId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(usage.item().get().asItem()).toString();
    if (!withUsagePage.contains(usageId)) {
        bad.add("JEI has no usage page for " + usageId + " (key "
                + theflogat.technomancy.compat.jei.JeiUsagePages.PREFIX + usage.path() + ")");
        pagesMissing++;
    }
    pagesChecked++;
}
out.append("usage pages JEI can find = ").append(pagesChecked - pagesMissing).append("/")
        .append(pagesChecked).append("\n");
if (pagesChecked < 30) {
    bad.add("only " + pagesChecked + " usage pages were declared, so this proves little");
}
if (!rawInfoKeys.isEmpty()) {
    bad.add("these usage pages rendered their key instead of their text: " + rawInfoKeys);
}

// ---- KubeJS ----
// KubeJS reads this file from the root of every mod jar, one plugin per line. A typo here is
// completely silent: KubeJS simply never loads the plugin. The file and the class are checked in
// every run; the plugin actually loading is checked only when KubeJS is installed, because the
// default development runtime deliberately leaves it out.
String plugins = null;
try (java.io.InputStream in = theflogat.technomancy.Technomancy.class
        .getResourceAsStream("/kubejs.plugins.txt")) {
    if (in == null) {
        bad.add("kubejs.plugins.txt is not on the classpath");
    } else {
        plugins = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
out.append("kubejs.plugins.txt = ").append(plugins == null ? "<missing>" : plugins.trim()).append("\n");
if (plugins != null && !plugins.contains("theflogat.technomancy.compat.kubejs.TechnomKubeJSPlugin")) {
    bad.add("kubejs.plugins.txt does not name the plugin class");
}
// The class has to ship even when KubeJS is absent, or the file above points at nothing.
if (theflogat.technomancy.Technomancy.class
        .getResource("/theflogat/technomancy/compat/kubejs/TechnomKubeJSPlugin.class") == null) {
    bad.add("the kubejs plugin class is not in the jar");
}

if (!net.minecraftforge.fml.ModList.get().isLoaded("kubejs")) {
    out.append("kubejs not installed; the plugin was not exercised "
            + "(start the client with -PwithKubejs=true to check that half)\n");
} else {
    // Reflective on purpose: the bridge compiles this probe, so a direct reference to a KubeJS
    // type would fail to compile in a runtime that does not have KubeJS at all.
    Class<?> registry = Class.forName("dev.latvian.mods.kubejs.util.KubeJSPlugins");
    java.util.List<?> loaded = (java.util.List<?>) registry.getMethod("getAll").invoke(null);
    // A lambda rather than a for-each: the bridge wraps this body, and a local named for the
    // thing being iterated collided with a name the wrapper already had in scope.
    boolean ours = loaded.stream().anyMatch(
            entry -> entry.getClass().getName()
                    .equals("theflogat.technomancy.compat.kubejs.TechnomKubeJSPlugin"));
    out.append("kubejs plugins loaded = ").append(loaded.size())
            .append(", ours present = ").append(ours).append("\n");
    if (!ours) {
        bad.add("KubeJS is installed but did not load the Technomancy plugin");
    }

    // The helper itself carries no KubeJS type, so it can be called directly in any runtime.
    var table2 = theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader.table();
    long ignis = theflogat.technomancy.compat.kubejs.TechnomJS.maxFuelValue("ignis");
    long perUnit = theflogat.technomancy.compat.kubejs.TechnomJS.maxEnergyPerUnit("ignis");
    out.append("Technom.maxFuelValue(ignis) = ").append(ignis)
            .append(", maxEnergyPerUnit = ").append(perUnit).append("\n");
    if (ignis != table2.maxFuelValue(dev.tc4port.thaumcraft.api.aspect.AspectId.parse("ignis"))) {
        bad.add("the kubejs binding disagrees with the fuel table about ignis");
    }
    // A mistyped aspect must fail loudly rather than answer the fallback, or a script author
    // reads a plausible number and never learns the id was wrong.
    boolean rejected = false;
    try {
        theflogat.technomancy.compat.kubejs.TechnomJS.maxFuelValue("technom:definitely_not_an_aspect");
    } catch (IllegalArgumentException expected) {
        rejected = true;
    }
    if (!rejected) {
        bad.add("the kubejs binding answered a fallback value for an unknown aspect");
    }
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
