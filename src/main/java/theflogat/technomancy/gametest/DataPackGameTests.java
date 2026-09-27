package theflogat.technomancy.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingRecipe;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchCategoryKey;
import dev.tc4port.thaumcraft.api.research.ResearchEntryFlag;
import dev.tc4port.thaumcraft.api.research.ResearchEntrySummary;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.research.ResearchCatalog;
import dev.tc4port.thaumcraft.research.ResearchEntryDefinition;
import dev.tc4port.thaumcraft.research.ResearchPageDefinition;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import theflogat.technomancy.Technomancy;

/**
 * Proves that the S1-B data pack was actually loaded by the server, not merely that the files
 * parse. A recipe with a bad ingredient id is logged and dropped, and a research entry that
 * fails validation takes its whole file with it, so "the build succeeded" says nothing here.
 *
 * <p>Two of the eight recipes and two of the four research entries produce content that is not
 * registered on this branch yet ({@code technom:essentia_dynamo}, {@code technom:energy_condenser}).
 * They carry a {@code forge:item_exists} condition, so every assertion about them is expressed as
 * "present exactly when the item is registered". That checks both directions: today it proves the
 * condition suppressed them cleanly, and the day the blocks land it starts proving they loaded,
 * with no edit to this test.</p>
 *
 * <p>Page-level assertions reach into {@code dev.tc4port.thaumcraft.research.ResearchCatalog}
 * rather than the {@code api} packages, because the public
 * {@link ResearchApi#clientPresentation(ResearchKey)} projection only exposes LORE and
 * EXTERNAL_LINK pages. That is acceptable here and nowhere else: this class is dev-only and is
 * excluded from the published jar.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataPackGameTests {

    private static final String BATCH = "technom_data";

    private static final String ESSENTIA_DYNAMO = "technom:essentia_dynamo";
    private static final String ENERGY_CONDENSER = "technom:energy_condenser";

    /** Written for {@code tools/validate_technom_data.py}; see that script's --inventory flag. */
    private static final String INVENTORY_FILE = "technom-data-inventory.json";

    private DataPackGameTests() {
    }

    /**
     * One authored recipe: where it lives, which serializer must accept it, what it must produce,
     * and the complete set of item ids its ingredients accept.
     *
     * <p>{@code serializer} rather than the recipe type on purpose: vanilla shaped and shapeless
     * crafting share the type {@code minecraft:crafting}, so only the serializer id proves which
     * codec accepted the file.</p>
     */
    private record ExpectedRecipe(String path, String serializer, String result, int resultCount,
            int ingredientCount, Set<String> acceptedItems, String research,
            Map<String, Integer> vis, String requires) {

        ResourceLocation id() {
            return new ResourceLocation(Technomancy.MOD_ID, this.path);
        }
    }

    private static List<ExpectedRecipe> expectedRecipes() {
        List<ExpectedRecipe> recipes = new ArrayList<>();
        recipes.add(new ExpectedRecipe("crucible/neutronized_metal", "thaumcraft:crucible",
                "technom:neutronized_metal", 1, 1, Set.of("thaumcraft:thaumium_ingot"),
                null, null, null));
        recipes.add(new ExpectedRecipe("enchanted_coil", "minecraft:crafting_shaped",
                "technom:enchanted_coil", 1, 3,
                Set.of("minecraft:redstone", "thaumcraft:thaumium_ingot"), null, null, null));
        recipes.add(new ExpectedRecipe("neutronized_gear", "minecraft:crafting_shaped",
                "technom:neutronized_gear", 1, 5,
                Set.of("technom:neutronized_metal", "minecraft:iron_ingot"), null, null, null));
        recipes.add(new ExpectedRecipe("potency_gem", "minecraft:crafting_shaped",
                "technom:potency_gem", 1, 5,
                Set.of("minecraft:redstone", "minecraft:quartz", "minecraft:gold_ingot"),
                null, null, null));
        recipes.add(new ExpectedRecipe("arcane/quantized_glass", "thaumcraft:arcane_shaped",
                "technom:quantized_glass", 4, 4, Set.of("minecraft:glass"),
                "technom:QUANTUMJARS", Map.of("ordo", 5, "ignis", 5), null));
        recipes.add(new ExpectedRecipe("arcane/quantum_jar", "thaumcraft:arcane_shaped",
                "technom:quantum_jar", 1, 8,
                Set.of("technom:quantized_glass", "technom:neutronized_metal"),
                "technom:QUANTUMJARS", Map.of("ordo", 15, "aqua", 10), null));
        recipes.add(new ExpectedRecipe("arcane/essentia_dynamo", "thaumcraft:arcane_shaped",
                ESSENTIA_DYNAMO, 1, 7,
                Set.of("technom:enchanted_coil", "technom:neutronized_gear",
                        "thaumcraft:essentia_tube", "thaumcraft:warded_jar"),
                "technom:DYNAMO",
                Map.of("aqua", 15, "ordo", 10, "ignis", 5, "perditio", 25), ESSENTIA_DYNAMO));
        recipes.add(new ExpectedRecipe("infusion/energy_condenser", "thaumcraft:infusion",
                ENERGY_CONDENSER, 1, 6,
                Set.of("thaumcraft:runic_matrix", "technom:neutronized_gear",
                        "technom:enchanted_coil", "thaumcraft:thaumium_block"),
                null, null, ENERGY_CONDENSER));
        return List.copyOf(recipes);
    }

    /** One authored research entry, including every page in order. */
    private record ExpectedEntry(String key, int column, int row, int complexity,
            Set<ResearchEntryFlag> flags, List<String> parents, Map<String, Integer> aspects,
            List<ExpectedPage> pages, List<String> requires) {

        ResearchKey researchKey() {
            return ResearchKey.parse(this.key);
        }
    }

    private record ExpectedPage(String type, String value, List<String> recipeIds, String policy) {
    }

    private static ExpectedPage text(String value) {
        return new ExpectedPage("TEXT", value, List.of(), "DISABLED");
    }

    private static ExpectedPage recipePage(String type, String recipeId) {
        return new ExpectedPage(type, "", List.of(recipeId), "COMPLETED_SINGLE");
    }

    private static List<ExpectedEntry> expectedEntries() {
        List<ExpectedEntry> entries = new ArrayList<>();
        entries.add(new ExpectedEntry("technom:TECHNOBASICS", 0, 0, 1,
                Set.of(ResearchEntryFlag.AUTO_UNLOCK, ResearchEntryFlag.ROUND,
                        ResearchEntryFlag.STUB),
                List.of(), Map.of(),
                List.of(text("technom.research_page.TECHNOBASICS.1"),
                        recipePage("CRUCIBLE_CRAFTING", "technom:crucible/neutronized_metal"),
                        recipePage("NORMAL_CRAFTING", "technom:enchanted_coil"),
                        recipePage("NORMAL_CRAFTING", "technom:neutronized_gear")),
                List.of()));
        entries.add(new ExpectedEntry("technom:QUANTUMJARS", 1, -2, 1,
                Set.of(ResearchEntryFlag.SECONDARY), List.of("technom:TECHNOBASICS"),
                Map.of("ordo", 5, "vacuos", 5, "praecantatio", 5),
                List.of(text("technom.research_page.QUANTUMJARS.1"),
                        recipePage("ARCANE_CRAFTING", "technom:arcane/quantum_jar"),
                        recipePage("ARCANE_CRAFTING", "technom:arcane/quantized_glass")),
                List.of()));
        entries.add(new ExpectedEntry("technom:DYNAMO", 2, 2, 3,
                Set.of(ResearchEntryFlag.SECONDARY), List.of("technom:TECHNOBASICS"),
                Map.of("machina", 5, "potentia", 5, "praecantatio", 5),
                List.of(text("technom.research_page.DYNAMO.1"),
                        text("technom.research_page.DYNAMO.2"),
                        recipePage("ARCANE_CRAFTING", "technom:arcane/essentia_dynamo"),
                        text("technom.research_page.DYNAMO.4"),
                        recipePage("NORMAL_CRAFTING", "technom:potency_gem")),
                List.of(ESSENTIA_DYNAMO)));
        // A-21: the 1.7.10 CONDENSER research was gated on the node dynamo's content switch, so
        // an essentia-dynamo-only install could never reach it. The only thing it depends on here
        // is its own block plus the block of its declared parent entry, which TC4R requires to
        // exist (ResearchCatalog "refers to missing parent" is a hard failure).
        entries.add(new ExpectedEntry("technom:CONDENSER", 2, 3, 3,
                Set.of(), List.of("technom:DYNAMO"),
                Map.of("potentia", 5, "ordo", 5, "permutatio", 5),
                List.of(text("technom.research_page.CONDENSER.1"),
                        recipePage("INFUSION_CRAFTING", "technom:infusion/energy_condenser")),
                List.of(ENERGY_CONDENSER, ESSENTIA_DYNAMO)));
        return List.copyOf(entries);
    }

    private static Map<String, Map<String, Integer>> expectedAspects() {
        Map<String, Map<String, Integer>> aspects = new LinkedHashMap<>();
        aspects.put("technom:neutronized_metal", Map.of("metallum", 4, "ordo", 2, "potentia", 2));
        aspects.put("technom:enchanted_coil", Map.of("metallum", 2, "potentia", 3, "praecantatio", 2));
        aspects.put("technom:neutronized_gear", Map.of("metallum", 6, "machina", 4, "ordo", 2));
        aspects.put("technom:potency_gem", Map.of("potentia", 6, "vitreus", 2, "lucrum", 2));
        aspects.put("technom:quantized_glass", Map.of("vitreus", 4, "ordo", 2, "ignis", 1));
        aspects.put("technom:quantum_jar",
                Map.of("vitreus", 12, "ordo", 8, "vacuos", 6, "metallum", 4));
        aspects.put(ESSENTIA_DYNAMO,
                Map.of("machina", 12, "potentia", 8, "aqua", 6, "perditio", 6, "metallum", 6));
        aspects.put(ENERGY_CONDENSER,
                Map.of("machina", 12, "potentia", 16, "permutatio", 6, "ordo", 6));
        return Map.copyOf(aspects);
    }

    private static boolean itemRegistered(String id) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id));
    }

    private static boolean gateOpen(List<String> requires) {
        return requires.stream().allMatch(DataPackGameTests::itemRegistered);
    }

    // ------------------------------------------------------------------------------------------

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void s1bRecipesLoadWithTheAuthoredContent(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        RegistryAccess registries = helper.getLevel().registryAccess();

        for (ExpectedRecipe expected : expectedRecipes()) {
            boolean gated = expected.requires() != null;
            boolean open = !gated || itemRegistered(expected.requires());
            Optional<? extends Recipe<?>> found = manager.byKey(expected.id());

            if (!open) {
                // The forge:item_exists condition must have removed it silently. If the
                // condition member were misspelled the recipe would instead have been parsed
                // and rejected with an "Unknown item" error in the log.
                helper.assertTrue(found.isEmpty(), expected.id()
                        + " loaded even though " + expected.requires() + " is not registered;"
                        + " the forge:item_exists condition did not take effect");
                continue;
            }

            helper.assertTrue(found.isPresent(), "recipe " + expected.id()
                    + " is missing from the recipe manager; a bad id is only logged, never fatal");
            Recipe<?> recipe = found.orElseThrow();

            ResourceLocation serializer =
                    BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
            helper.assertTrue(expected.serializer().equals(String.valueOf(serializer)),
                    expected.id() + " deserialized with " + serializer + " instead of "
                            + expected.serializer());

            ItemStack result = recipe.getResultItem(registries);
            ResourceLocation resultId = ForgeRegistries.ITEMS.getKey(result.getItem());
            helper.assertTrue(expected.result().equals(String.valueOf(resultId)),
                    expected.id() + " produces " + resultId + " instead of " + expected.result());
            helper.assertTrue(result.getCount() == expected.resultCount(), expected.id()
                    + " produces " + result.getCount() + " items instead of "
                    + expected.resultCount());

            List<Ingredient> ingredients = recipe.getIngredients().stream()
                    .filter(ingredient -> !ingredient.isEmpty()).toList();
            helper.assertTrue(ingredients.size() == expected.ingredientCount(), expected.id()
                    + " has " + ingredients.size() + " non-empty ingredients instead of "
                    + expected.ingredientCount()
                    + "; a collapsed candidate list would show up here");
            Set<String> accepted = new TreeSet<>();
            for (Ingredient ingredient : ingredients) {
                for (ItemStack candidate : ingredient.getItems()) {
                    accepted.add(String.valueOf(ForgeRegistries.ITEMS.getKey(candidate.getItem())));
                }
            }
            helper.assertTrue(accepted.equals(new TreeSet<>(expected.acceptedItems())),
                    expected.id() + " accepts " + accepted + " instead of "
                            + new TreeSet<>(expected.acceptedItems()));

            if (expected.research() != null || expected.vis() != null) {
                helper.assertTrue(recipe instanceof ArcaneCraftingRecipe,
                        expected.id() + " is not an ArcaneCraftingRecipe: " + recipe.getClass());
                ArcaneCraftingRecipe arcane = (ArcaneCraftingRecipe) recipe;
                helper.assertTrue(arcane.research().key()
                                .equals(Optional.of(ResearchKey.parse(expected.research()))),
                        expected.id() + " is gated on " + arcane.research().key()
                                + " instead of " + expected.research()
                                + "; a misspelled \"research\" field is silently treated as absent");
                Map<VisChannel, Integer> vis = new LinkedHashMap<>();
                expected.vis().forEach((channel, amount) -> vis.put(
                        VisChannel.valueOf(channel.toUpperCase(java.util.Locale.ROOT)), amount));
                helper.assertTrue(arcane.vis().equals(vis),
                        expected.id() + " costs " + arcane.vis() + " instead of " + vis);
            }
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void s1bResearchEntriesLoadIntoTheTc4rCatalog(GameTestHelper helper) {
        ResearchCategoryKey category = ResearchCategoryKey.parse("technom:TECHNOMANCY");
        helper.assertTrue(ResearchApi.registry().categories().containsKey(category),
                "research category " + category + " is missing; TC4R throws on an entry that"
                        + " refers to a missing category, so this would take the tree with it");

        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Map<ResearchKey, ResearchEntrySummary> entries = ResearchApi.registry().entries();

        for (ExpectedEntry expected : expectedEntries()) {
            ResearchKey key = expected.researchKey();
            boolean open = gateOpen(expected.requires());
            if (!open) {
                helper.assertTrue(!entries.containsKey(key), key
                        + " loaded even though " + expected.requires()
                        + " is not registered; its forge:conditions did not take effect");
                continue;
            }
            helper.assertTrue(entries.containsKey(key), "research " + key
                    + " is not in the TC4R catalog");
            ResearchEntrySummary summary = entries.get(key);
            helper.assertTrue(summary.category().equals(category),
                    key + " is in category " + summary.category());
            helper.assertTrue(summary.displayColumn() == expected.column()
                            && summary.displayRow() == expected.row(),
                    key + " sits at (" + summary.displayColumn() + ", " + summary.displayRow()
                            + ") instead of (" + expected.column() + ", " + expected.row() + ")");
            helper.assertTrue(summary.complexity() == expected.complexity(),
                    key + " has complexity " + summary.complexity() + " instead of "
                            + expected.complexity());
            helper.assertTrue(summary.flags().equals(expected.flags()),
                    key + " has flags " + summary.flags() + " instead of " + expected.flags());
            List<ResearchKey> parents = expected.parents().stream().map(ResearchKey::parse).toList();
            helper.assertTrue(summary.parents().equals(parents),
                    key + " has parents " + summary.parents() + " instead of " + parents);
            Map<AspectId, Integer> aspects = new LinkedHashMap<>();
            expected.aspects().forEach((id, amount) -> aspects.put(AspectId.parse(id), amount));
            helper.assertTrue(summary.aspects().amounts().equals(aspects),
                    key + " costs " + summary.aspects().amounts() + " instead of " + aspects);

            ResearchEntryDefinition definition = ResearchCatalog.get(key);
            helper.assertTrue(definition != null, key + " has no loaded definition");
            List<ResearchPageDefinition> pages = definition.pages();
            helper.assertTrue(pages.size() == expected.pages().size(), key + " has "
                    + pages.size() + " pages instead of " + expected.pages().size());
            for (int index = 0; index < pages.size(); index++) {
                ResearchPageDefinition page = pages.get(index);
                ExpectedPage wanted = expected.pages().get(index);
                String where = key + " page " + (index + 1);
                helper.assertTrue(page.type().name().equals(wanted.type()),
                        where + " is a " + page.type() + " instead of " + wanted.type());
                helper.assertTrue(page.value().equals(wanted.value()),
                        where + " points at \"" + page.value() + "\" instead of \""
                                + wanted.value() + "\"");
                helper.assertTrue(page.recipeLinkPolicy().name().equals(wanted.policy()),
                        where + " links with " + page.recipeLinkPolicy() + " instead of "
                                + wanted.policy());
                List<String> ids = page.recipeIds().stream().map(ResourceLocation::toString).toList();
                helper.assertTrue(ids.equals(wanted.recipeIds()),
                        where + " references " + ids + " instead of " + wanted.recipeIds());
                for (ResourceLocation recipeId : page.recipeIds()) {
                    // Research pages store plain resource locations: TC4R never checks that they
                    // resolve, so a typo here is an empty page in the Thaumonomicon, not an error.
                    helper.assertTrue(manager.byKey(recipeId).isPresent(),
                            where + " references recipe " + recipeId
                                    + ", which is not loaded");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void s1bObjectAspectsResolveThroughAspectQueryApi(GameTestHelper helper) {
        expectedAspects().forEach((id, wanted) -> {
            ResourceLocation itemId = new ResourceLocation(id);
            if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
                // Unknown ids in a thaumcraft/object_aspects file are a hard IllegalStateException
                // that kills the whole file, which is why the two pending blocks live in their own
                // condition-gated files. Nothing to assert until they are registered.
                return;
            }
            AspectAmounts resolved =
                    AspectQueryApi.item(new ItemStack(ForgeRegistries.ITEMS.getValue(itemId)));
            Map<AspectId, Integer> expected = new LinkedHashMap<>();
            wanted.forEach((aspect, amount) -> {
                AspectId parsed = AspectId.parse(aspect);
                helper.assertTrue(AspectApi.get(parsed) != null,
                        "aspect " + parsed + " does not exist");
                expected.put(parsed, amount);
            });
            helper.assertTrue(resolved.amounts().equals(expected), id + " resolves to "
                    + resolved.amounts() + " instead of the authored " + expected
                    + "; a direct entry must win over TC4R's recipe inference");
        });
        helper.succeed();
    }

    /**
     * Writes the registry inventory that {@code tools/validate_technom_data.py} checks the data
     * files against. A static allow-list would drift; this is the set of ids the running server
     * actually has.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void s1bWritesRegistryInventoryForTooling(GameTestHelper helper) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("mod_id", Technomancy.MOD_ID);

        root.add("items", strings(ForgeRegistries.ITEMS.getKeys().stream()
                .map(ResourceLocation::toString).toList()));
        root.add("item_tags", strings(BuiltInRegistries.ITEM.getTagNames()
                .map(tag -> tag.location().toString()).sorted().toList()));
        root.add("recipe_types", strings(BuiltInRegistries.RECIPE_TYPE.keySet().stream()
                .map(ResourceLocation::toString).sorted().toList()));
        root.add("recipe_serializers", strings(BuiltInRegistries.RECIPE_SERIALIZER.keySet().stream()
                .map(ResourceLocation::toString).sorted().toList()));
        root.add("aspects", strings(AspectApi.registry().definitions().keySet().stream()
                .map(AspectId::serialized).sorted().toList()));

        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        JsonObject recipes = new JsonObject();
        manager.getRecipes().forEach(recipe -> {
            ResourceLocation id = recipe.getId();
            if (Technomancy.MOD_ID.equals(id.getNamespace())) {
                recipes.addProperty(id.toString(),
                        String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER
                                .getKey(recipe.getSerializer())));
            }
        });
        root.add("mod_recipes", recipes);
        root.addProperty("recipe_total", manager.getRecipes().size());

        JsonObject research = new JsonObject();
        ResearchApi.registry().entries().forEach((key, entry) ->
                research.addProperty(key.toString(), entry.category().toString()));
        root.add("research_entries", research);
        root.add("research_categories", strings(ResearchApi.registry().categories().keySet()
                .stream().map(ResearchCategoryKey::toString).sorted().toList()));

        Path target = Path.of(INVENTORY_FILE).toAbsolutePath();
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (IOException exception) {
            helper.fail("could not write " + target + ": " + exception);
            return;
        }
        Technomancy.LOGGER.info("GameTest wrote registry inventory to {} ({} items, {} recipes,"
                + " {} research entries)", target, ForgeRegistries.ITEMS.getKeys().size(),
                manager.getRecipes().size(), ResearchApi.registry().entries().size());
        helper.assertTrue(Files.isRegularFile(target), "inventory file was not created");
        helper.succeed();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        new LinkedHashSet<>(values).forEach(array::add);
        return array;
    }
}
