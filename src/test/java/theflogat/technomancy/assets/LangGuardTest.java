package theflogat.technomancy.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Text that the mod asks the game for by key has to exist, in every language, or the player sees
 * the key itself.
 *
 * <p>A missing translation is completely silent everywhere else: the JSON is valid, the code
 * compiles, the server never touches it, and no GameTest renders a GUI. The ritual tome made it
 * worse than a cosmetic problem - its whole text is now looked up by key, so one typo would have
 * turned a page into a wall of {@code technom.tome.page.…}. JEI usage pages and Jade tooltips are
 * the same shape of risk.</p>
 *
 * <p>The keys are recovered from the sources that ask for them rather than from a hand-kept list,
 * so a new page is covered the moment it is written. That does mean a source that changes shape
 * breaks the guard; the patterns below are deliberately narrow and the failure is loud.</p>
 */
class LangGuardTest {

    private static final String LANG_DIR = "assets/technom/lang";
    /** The languages the mod ships. Every one of them must be complete. */
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn");

    private static final Path JEI_PAGES =
            Path.of("src/main/java/theflogat/technomancy/compat/jei/JeiUsagePages.java");
    private static final Path TOME_CONTENT =
            Path.of("src/main/java/theflogat/technomancy/client/screen/RitualTomeContent.java");

    /**
     * The two languages must agree on their keys exactly. A key present in only one of them is
     * either dead text or a hole, and the hole only shows up in that one language - which is
     * exactly the kind of thing that ships.
     */
    @Test
    void everyLanguageHasTheSameKeys() {
        Set<String> reference = keys(LANGUAGES.get(0));
        for (String language : LANGUAGES) {
            assertEquals(reference, keys(language),
                    "the " + language + " language file does not have the same keys as "
                            + LANGUAGES.get(0));
        }
    }

    /**
     * Every JEI usage page the plugin registers needs its text.
     *
     * <p>{@link theflogat.technomancy.compat.jei.JeiUsagePages} cannot be loaded here - it reads
     * registry objects, which need Forge bootstrapped - so its source is read instead. The
     * pattern matches the {@code Page.of(item, "path")} and {@code Page.botania(item, "path")}
     * calls, which is the only place a path is written down.</p>
     */
    @Test
    void everyJeiUsagePageHasText() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("Page\\.(?:of|botania)\\([^,]+,\\s*\"([^\"]+)\"\\)")
                .matcher(read(JEI_PAGES));
        while (matcher.find()) {
            paths.add("technom.jei.info." + matcher.group(1));
        }
        assertTrue(paths.size() >= 30, "only found " + paths.size() + " usage pages in "
                + JEI_PAGES + "; the pattern no longer matches the file");
        assertHasText(paths, "JEI usage page");
    }

    /**
     * Every tab, chapter label and page body the ritual tome builds needs its text.
     *
     * <p>The three helpers in {@code RitualTomeContent} - {@code new Tab(key, icon)},
     * {@code entry(path)} and {@code page(path)} - are the only ways a tome key is spelled, and
     * they map to {@code technom.tome.{tab,entry,page}} respectively. A tab already passes its
     * whole key, so it is matched as-is.</p>
     */
    @Test
    void everyRitualTomeKeyHasText() throws IOException {
        String source = read(TOME_CONTENT);
        Set<String> keys = new LinkedHashSet<>();
        collect(keys, source, "new Tab\\(\"([^\"]+)\"", "");
        collect(keys, source, "entry\\(\"([^\"]+)\"\\)", "technom.tome.entry.");
        collect(keys, source, "page\\(\"([^\"]+)\"\\)", "technom.tome.page.");
        assertTrue(keys.size() >= 50, "only found " + keys.size() + " tome keys in " + TOME_CONTENT
                + "; the pattern no longer matches the file");
        assertHasText(keys, "ritual tome key");
    }

    // ---- helpers ----

    private static void collect(Set<String> into, String source, String pattern, String prefix) {
        Matcher matcher = Pattern.compile(pattern).matcher(source);
        while (matcher.find()) {
            into.add(prefix + matcher.group(1));
        }
    }

    /** Every key must be present in every language, and must not be blank in any of them. */
    private static void assertHasText(Set<String> keys, String what) {
        List<String> missing = new ArrayList<>();
        List<String> blank = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject lang = lang(language);
            for (String key : keys) {
                JsonElement value = lang.get(key);
                if (value == null || value.isJsonNull()) {
                    missing.add(language + " -> " + key);
                } else if (value.getAsString().isBlank()) {
                    blank.add(language + " -> " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "these " + what + "s have no text: " + missing);
        assertTrue(blank.isEmpty(), "these " + what + "s are blank: " + blank);
    }

    private static Set<String> keys(String language) {
        return new LinkedHashSet<>(lang(language).keySet());
    }

    private static JsonObject lang(String language) {
        String resource = LANG_DIR + "/" + language + ".json";
        try (InputStream stream = LangGuardTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(stream != null, resource + " is not on the classpath; did processResources"
                    + " run?");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                assertTrue(json.size() >= 200, resource + " only has " + json.size()
                        + " keys, so it is not the file the mod ships");
                return json;
            }
        } catch (IOException e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }

    /** A source file, relative to the project directory the test task runs in. */
    private static String read(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file + " is not there; the test task's working"
                + " directory is not the project directory");
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
