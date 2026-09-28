package theflogat.technomancy.gametest;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.rituals.RitualRegistry;

/**
 * S3 rituals: the registry holds the original's sixteen, every crystal kind is a core somewhere,
 * and each kind has both the crystal that builds the frame and the catalyst that runs it.
 * Written for the S3 batch run.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S3RitualGameTests {

    private static final String BATCH = "technom_s3_rituals";
    /** The original registered sixteen; FireT3 stayed commented out there as well. */
    private static final int EXPECTED = 16;

    private S3RitualGameTests() {
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void sixteenRitualsAreRegistered(GameTestHelper helper) {
        List<Ritual> rituals = RitualRegistry.all();
        S2StorageGameTests.check(helper, rituals.size() == EXPECTED,
                "expected " + EXPECTED + " rituals, found " + rituals.size());
        for (Ritual.Type type : Ritual.Type.values()) {
            boolean used = rituals.stream().anyMatch(ritual -> ritual.core() == type);
            S2StorageGameTests.check(helper, used, "no ritual uses " + type + " as its core");
        }
        Technomancy.LOGGER.info("GameTest rituals: {} registered", rituals.size());
        helper.succeed();
    }

    /** Every crystal and catalyst kind exists, or the frame that needs it cannot be built. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyRitualKindHasACrystalAndACatalyst(GameTestHelper helper) {
        for (Ritual.Type type : Ritual.Type.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            S2StorageGameTests.check(helper, path(crystal(type)).equals("crystal_" + name),
                    "no crystal block for " + type);
            S2StorageGameTests.check(helper, path(catalyst(type)).equals("catalyst_" + name),
                    "no catalyst block for " + type);
        }
        helper.succeed();
    }

    private static String path(RegistryObject<Block> block) {
        return BuiltInRegistries.BLOCK.getKey(block.get()).getPath();
    }

    private static RegistryObject<Block> crystal(Ritual.Type type) {
        return switch (type) {
            case EARTH -> TechnomBlocks.CRYSTAL_EARTH;
            case FIRE -> TechnomBlocks.CRYSTAL_FIRE;
            case WATER -> TechnomBlocks.CRYSTAL_WATER;
            case LIGHT -> TechnomBlocks.CRYSTAL_LIGHT;
            case DARK -> TechnomBlocks.CRYSTAL_DARK;
        };
    }

    private static RegistryObject<Block> catalyst(Ritual.Type type) {
        return switch (type) {
            case EARTH -> TechnomBlocks.CATALYST_EARTH;
            case FIRE -> TechnomBlocks.CATALYST_FIRE;
            case WATER -> TechnomBlocks.CATALYST_WATER;
            case LIGHT -> TechnomBlocks.CATALYST_LIGHT;
            case DARK -> TechnomBlocks.CATALYST_DARK;
        };
    }
}
