package theflogat.technomancy.assets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every item icon the mod ships has to be able to draw something.
 *
 * <p>An item whose block is drawn by a {@code BlockEntityRenderer} needs its model to be
 * {@code "parent": "builtin/entity"}. {@code ItemRenderer} only consults
 * {@code IClientItemExtensions.getCustomRenderer()} once the baked model reports
 * {@code isCustomRenderer()}, and the only thing that produces such a model is
 * {@code ModelBakery}'s {@code builtin/entity} marker. Every other item model takes the ordinary
 * path, where a model with no {@code elements} draws nothing at all.</p>
 *
 * <p>That is how seven machines ended up with blank inventory icons. Their block models are empty
 * shells - upstream's {@code renderWorldBlock} returned {@code false}, so every pixel comes from
 * the renderer - and their item models were left pointing at those shells. Nothing complains: the
 * JSON is valid, the model bakes, {@code javac} is happy and so is every server-side GameTest. It
 * is only visible to a client, so it is guarded here instead.</p>
 *
 * <p>The rules below are written against the models rather than a list of names, so adding a
 * machine to the code-drawn family is enough to bring its icon under the guard.</p>
 */
class ItemModelGuardTest {

    private static final String ITEM_DIR = "assets/technom/models/item";
    private static final String CUSTOM_RENDERER = "builtin/entity";
    /** A parent chain longer than this is a cycle or a mistake; the loader caps it too. */
    private static final int MAX_DEPTH = 8;

    /**
     * An item model must either declare geometry or ask for a custom renderer. A model that does
     * neither is an invisible icon.
     */
    @Test
    void everyItemModelEitherDrawsGeometryOrAsksForACustomRenderer() throws IOException {
        List<String> blank = new ArrayList<>();
        for (String name : jsonNames(ITEM_DIR)) {
            JsonObject json = read(ITEM_DIR, name);
            if (isCustomRenderer(json)) {
                continue;
            }
            if (!hasGeometry(json, 0)) {
                blank.add(name);
            }
        }
        assertTrue(blank.isEmpty(), "these item models resolve to a model with no elements, so"
                + " their icons would be blank: " + blank + ". An item whose block is drawn by a"
                + " BlockEntityRenderer must use \"parent\": \"" + CUSTOM_RENDERER + "\".");
    }

    /**
     * A code-drawn item must carry a {@code display} block. {@code ItemRenderer} applies the
     * display transform and then translates by {@code (-0.5, -0.5, -0.5)} before handing the pose
     * to {@code renderByItem}, so the renderer's job is to centre the model on
     * {@code (0.5, 0.5, 0.5)} - and without a display there is no rotation, scale or offset at
     * all and the icon comes out unrotated and the wrong size.
     */
    @Test
    void everyCustomRendererItemModelCarriesADisplayBlock() throws IOException {
        List<String> found = new ArrayList<>();
        for (String name : jsonNames(ITEM_DIR)) {
            JsonObject json = read(ITEM_DIR, name);
            if (!isCustomRenderer(json)) {
                continue;
            }
            found.add(name);
            JsonObject display = json.has("display") ? json.getAsJsonObject("display") : null;
            assertTrue(display != null && display.has("gui"),
                    ITEM_DIR + "/" + name + " is code-drawn but declares no display.gui, so its"
                            + " icon would be drawn with no rotation and no scale");
        }
        assertTrue(found.size() >= 9, "only " + found.size() + " item models ask for a custom"
                + " renderer; the code-drawn machines and the five crystals should all be here");
    }

    /**
     * A code-drawn item still needs a particle texture - it is the fallback the item is drawn with
     * when the renderer has nothing to show, and a missing one is a purple-and-black checkerboard
     * rather than an error.
     */
    @Test
    void everyCustomRendererItemModelHasAnExistingParticleTexture() throws IOException {
        for (String name : jsonNames(ITEM_DIR)) {
            JsonObject json = read(ITEM_DIR, name);
            if (!isCustomRenderer(json)) {
                continue;
            }
            JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : null;
            assertTrue(textures != null && textures.has("particle"),
                    ITEM_DIR + "/" + name + " has no particle texture");
            String particle = textures.get("particle").getAsString();
            assertTrue(textureExists(particle),
                    ITEM_DIR + "/" + name + " points its particle at " + particle
                            + ", which is not on the classpath");
        }
    }

    // ---- resolution ----

    private static boolean isCustomRenderer(JsonObject json) {
        String parent = string(json, "parent");
        return parent != null && CUSTOM_RENDERER.equals(parent.indexOf(':') < 0
                ? parent
                : parent.substring(parent.indexOf(':') + 1));
    }

    /**
     * Vanilla templates whose geometry the loader synthesises rather than reading from
     * {@code elements}: {@code ItemModelGenerator} builds the icon out of {@code layer0} and its
     * siblings, so a chain that ends in one of these does draw something.
     *
     * <p>The whole {@code minecraft:item/} family is taken this way rather than naming
     * {@code generated} and {@code handheld} individually, because the vanilla assets are not on
     * the unit-test classpath and none of them is the empty-shell case this guard hunts for.</p>
     */
    private static boolean isProcedural(String id) {
        return "builtin/generated".equals(id) || id.startsWith("minecraft:item/");
    }

    /** Whether any model in the parent chain draws something. */
    private static boolean hasGeometry(JsonObject json, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            return false;
        }
        JsonElement elements = json.get("elements");
        if (elements != null && elements.isJsonArray() && !elements.getAsJsonArray().isEmpty()) {
            return true;
        }
        String parent = string(json, "parent");
        if (parent == null) {
            return false;
        }
        // A parent with no namespace is resolved against minecraft, as the loader does.
        String id = parent.indexOf(':') < 0 ? "minecraft:" + parent : parent;
        if (isCustomRenderer(json) || isProcedural(id)) {
            return true;
        }
        // A parent that is still not on the classpath is one of the vanilla templates above by
        // another name; treating a miss as "no geometry" is the safe reading.
        JsonObject resolved = readOrNull(resourceOf(id));
        return resolved != null && hasGeometry(resolved, depth + 1);
    }

    /** {@code technom:block/x} -> {@code assets/technom/models/block/x.json}. */
    private static String resourceOf(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    private static boolean textureExists(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return resource("assets/" + namespace + "/textures/" + path + ".png") != null;
    }

    // ---- files ----

    private static List<String> jsonNames(String directory) throws IOException {
        Path dir = directory(directory);
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static Path directory(String resourceDirectory) throws IOException {
        URL url = resource(resourceDirectory);
        assertTrue(url != null, resourceDirectory + " is not on the classpath; did"
                + " processResources run?");
        try {
            // toURI, not getPath: on Windows getPath yields "/H:/..." and Path.of rejects it.
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(resourceDirectory + " is not a directory on disk: " + url, e);
        }
    }

    private static JsonObject read(String directory, String name) throws IOException {
        Path file = directory(directory).resolve(name);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject readOrNull(String resource) throws IOException {
        URL url = resource(resource);
        if (url == null) {
            return null;
        }
        try (InputStream stream = url.openStream();
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static URL resource(String path) {
        return ItemModelGuardTest.class.getClassLoader().getResource(path);
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
