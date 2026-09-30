package theflogat.technomancy.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;

/**
 * A rule that every block this mod draws with code has to keep: it must not occlude.
 *
 * <p>Upstream drew these with a {@code renderWorldBlock} that returned {@code false}, and every
 * one of them declared {@code isOpaqueCube() == false}. The port keeps the same arrangement - the
 * blockstate model is an empty shell and a {@code BlockEntityRenderer} supplies the pixels - but
 * the occlusion flag lives in the block's {@code Properties}, far away from the renderer, so it is
 * easy to leave out.</p>
 *
 * <p>Leaving it out is invisible to every other test and only shows up on a client: the block
 * reports {@code canOcclude() == true}, {@code BlockStateBase.isSolidRender} short-circuits on
 * exactly that flag, and every neighbour culls the face it shares with this block. The gap is then
 * filled by whatever the model draws there - which for an empty shell is nothing, so the
 * neighbouring blocks appear to have a hole cut out of them.</p>
 *
 * <p>The set of blocks under the rule is derived rather than listed: an item model that asks for
 * {@code builtin/entity} is the marker for "drawn by code", the same marker
 * {@code ItemModelGuardTest} uses on the item side. Adding a machine to the code-drawn family
 * brings its block under this guard automatically.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CodeDrawnBlockGameTests {

    private static final String BATCH = "technom_code_drawn";
    private static final String ITEM_DIR = "assets/technom/models/item";
    private static final String CUSTOM_RENDERER = "builtin/entity";

    private CodeDrawnBlockGameTests() {
    }

    /**
     * A block whose item model asks for a custom renderer is drawn by code, so it must not
     * occlude. {@code noOcclusion()} is the only thing that sets {@code canOcclude} to false.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyCodeDrawnBlockIsNonOccluding(GameTestHelper helper) {
        int checked = 0;
        for (String name : codeDrawnItemNames()) {
            ResourceLocation id = new ResourceLocation(Technomancy.MOD_ID, name);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                // A code-drawn item that is not a block has no occlusion flag to check.
                continue;
            }
            checked++;
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            S2StorageGameTests.check(helper, !state.canOcclude(),
                    id + " is drawn by code (its item model is " + CUSTOM_RENDERER + ") but still"
                            + " occludes, so every neighbour culls the face it shares with it and"
                            + " the empty-shell model leaves a hole. Add .noOcclusion() to its"
                            + " Properties.");
        }
        S2StorageGameTests.check(helper, checked >= 14,
                "only " + checked + " code-drawn blocks were found; the seven machines, the five"
                        + " crystals and the two burners should all be here");
        Technomancy.LOGGER.info("GameTest code-drawn: {} code-drawn blocks all non-occluding",
                checked);
        helper.succeed();
    }

    // ---- the code-drawn family, read off the item models ----

    /** Item model file names whose model asks for a custom renderer, without the extension. */
    private static List<String> codeDrawnItemNames() {
        try {
            Path dir = itemDirectory();
            List<String> names = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".json")) {
                        continue;
                    }
                    if (isCustomRenderer(read(file))) {
                        names.add(fileName.substring(0, fileName.length() - ".json".length()));
                    }
                }
            }
            names.sort(String::compareTo);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + ITEM_DIR + " from the classpath", e);
        }
    }

    private static boolean isCustomRenderer(JsonObject json) {
        JsonElement parent = json.get("parent");
        if (parent == null || parent.isJsonNull()) {
            return false;
        }
        String id = parent.getAsString();
        return CUSTOM_RENDERER.equals(id.indexOf(':') < 0 ? id : id.substring(id.indexOf(':') + 1));
    }

    private static JsonObject read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path itemDirectory() throws IOException {
        URL url = CodeDrawnBlockGameTests.class.getClassLoader().getResource(ITEM_DIR);
        if (url == null) {
            throw new IOException(ITEM_DIR + " is not on the classpath; did processResources run?");
        }
        try {
            // toURI, not getPath: on Windows getPath yields "/H:/..." and Path.of rejects it.
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(ITEM_DIR + " is not a directory on disk: " + url, e);
        }
    }
}
