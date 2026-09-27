package theflogat.technomancy.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.compat.gtceu.GtceuPresence;

/**
 * Optional-GTCEu isolation checks. They pass both with and without GTCEu on the runtime,
 * which is the point: the same code must be safe in either environment.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GtceuIsolationGameTests {
    /** Set by the {@code gameTestServer} run from {@code -PwithGtceu}; absent elsewhere. */
    static final String EXPECT_GTCEU_PROPERTY = "technom.gametest.expectGtceu";
    private static final String GT_MOD_CLASS = "com.gregtechceu.gtceu.GTCEu";
    // Computed at run time so this class's own constant pool never holds the searched name.
    private static final String GT_INTERNAL_PREFIX = "com.gregtechceu.".replace('.', '/');
    private static final String OWN_ROOT = "theflogat/technomancy/";
    private static final String GT_ADAPTER_ROOT = OWN_ROOT + "compat/gtceu/";
    private static final String GAMETEST_ROOT = OWN_ROOT + "gametest/";
    private static final String PRESENCE_CLASS = GT_ADAPTER_ROOT + "GtceuPresence";

    private GtceuIsolationGameTests() {}

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = "technom_smoke")
    public static void presenceMatchesModListAndClasspath(GameTestHelper helper) {
        boolean loaded = GtceuPresence.isLoaded();
        IModFileInfo gtFile = ModList.get().getModFileById(GtceuPresence.MOD_ID);
        helper.assertTrue(loaded == (gtFile != null),
                "GtceuPresence=" + loaded + " but ModList mod file present=" + (gtFile != null));
        boolean visible = isClassVisible(GT_MOD_CLASS);
        helper.assertTrue(loaded == visible,
                "GtceuPresence=" + loaded + " but " + GT_MOD_CLASS + " visible=" + visible);

        String expected = System.getProperty(EXPECT_GTCEU_PROPERTY);
        if (expected != null) {
            helper.assertTrue(Boolean.parseBoolean(expected) == loaded,
                    "dev runtime was built with withGtceu=" + expected + " but GTCEu loaded=" + loaded);
        }
        Technomancy.LOGGER.info("GameTest GTCEu presence: loaded={}, expected={}", loaded,
                expected == null ? "unspecified" : expected);
        helper.succeed();
    }

    /**
     * Linking against GT is only allowed in isolated adapter classes under {@code compat/gtceu},
     * never in the presence gate. Test-only classes are not shipped and are skipped.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = "technom_smoke")
    public static void onlyIsolatedAdapterClassesLinkGtceu(GameTestHelper helper) {
        IModFileInfo self = ModList.get().getModFileById(Technomancy.MOD_ID);
        helper.assertTrue(self != null, "own mod file is not in the ModList");
        Path root = self.getFile().findResource(OWN_ROOT.split("/"));

        List<String> scanned = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = OWN_ROOT + root.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                if (name.startsWith(GAMETEST_ROOT)) {
                    continue;
                }
                scanned.add(name);
                boolean linksGt = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
                        .contains(GT_INTERNAL_PREFIX);
                boolean isAdapter = name.startsWith(GT_ADAPTER_ROOT) && !name.equals(PRESENCE_CLASS)
                        && !name.startsWith(PRESENCE_CLASS + "$");
                if (linksGt && !isAdapter) {
                    offenders.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot scan own classes under " + root, e);
        }

        helper.assertTrue(scanned.contains(OWN_ROOT + "Technomancy") && scanned.contains(PRESENCE_CLASS),
                "class scan did not see the entry point and presence gate: " + scanned);
        helper.assertTrue(offenders.isEmpty(), "classes outside the GT adapter link GTCEu: " + offenders);
        Technomancy.LOGGER.info("GameTest GTCEu isolation: scanned {} shipped classes, 0 link GTCEu outside {}",
                scanned.size(), GT_ADAPTER_ROOT);
        helper.succeed();
    }

    private static boolean isClassVisible(String className) {
        try {
            Class.forName(className, false, GtceuIsolationGameTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
