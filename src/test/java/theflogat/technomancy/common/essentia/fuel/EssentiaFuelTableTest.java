package theflogat.technomancy.common.essentia.fuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Exercises the shipped fuel table through the real codec and the real resolver.
 *
 * <p>The table under test is the file the mod actually ships, read off the classpath, so these
 * are assertions about the data players will get rather than about a copy of it. The expected
 * numbers come from {@code TileEssentiaDynamo.getAspectFuel} (1.7.10, lines 61-150) with the
 * three corrections recorded in the spec: Terra, Exanimis outside the End, and Permutatio.</p>
 */
class EssentiaFuelTableTest {

    private static final String SHIPPED = "/data/technom/technomancy/essentia_fuel/default.json";
    /** The project default; asserting against it keeps the config comment honest. */
    private static final double SCALE = 0.25;

    private static EssentiaFuelTable shipped() {
        return EssentiaFuelLoader.parse(Map.of(new ResourceLocation("technom", "default"), read(SHIPPED)));
    }

    private static JsonElement read(String resource) {
        try (InputStream stream = EssentiaFuelTableTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "missing shipped resource " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static AspectId aspect(String name) {
        return AspectId.parse(name);
    }

    private static int value(EssentiaFuelTable table, String name, FuelEnvironment environment) {
        return table.fuelValue(environment, aspect(name));
    }

    // ---- the condenser loop, checked against what the dynamo will really burn ----

    /**
     * The startup balance check runs before any data pack is read, so it uses a constant. This
     * is what keeps that constant honest: if the shipped table ever changes potentia's value,
     * the startup check would otherwise go on judging a loop that no longer exists.
     */
    @Test
    void theStartupConstantIsTheValueTheShippedTableReallyGivesPotentia() {
        EssentiaFuelTable table = shipped();
        assertEquals(theflogat.technomancy.common.machines.CondenserBalance.SHIPPED_POTENTIA_FUEL_VALUE,
                table.maxFuelValue(aspect("potentia")));
        assertEquals(table.maxFuelValue(aspect("potentia")),
                value(table, "potentia", new TestFuelEnvironment()), "potentia is unconditional");
    }

    /**
     * The balance check and the dynamo must agree on the Q a unit is worth, or the check is
     * testing a machine that does not exist. Both now go through the same function.
     */
    @Test
    void theBalanceCheckUsesTheDynamosOwnConversion() {
        for (double scale : new double[] {0.25, 0.5, 1.0, 2.5}) {
            assertEquals(EssentiaFuelTable.energyPerUnit(800, scale),
                    theflogat.technomancy.common.machines.CondenserBalance.dynamoYieldPerUnitQ(800, scale),
                    "at scale " + scale);
        }
    }

    /**
     * A data pack that raises potentia's fuel value turns "condenser into dynamo" into free
     * energy without touching the config, and a conditional or random value only has to pay off
     * in one place to be exploitable - so the reload check has to use the best value anywhere.
     */
    @Test
    void aDataPackCanMakeTheLoopProfitableAndTheBestValueAnywhereIsWhatCounts() {
        EssentiaFuelTable generous = EssentiaFuelLoader.parse(Map.of(
                new ResourceLocation("technom", "default"), read(SHIPPED),
                new ResourceLocation("somepack", "potentia"), json("""
                        {"entries": [{"aspects": ["potentia"], "value": 800,
                          "when": [{"condition": {"type": "technom:dimension", "dimension": "minecraft:the_end"},
                                    "value": 12000}],
                          "random_bonus": 500}]}
                        """)));
        int best = generous.maxFuelValue(aspect("potentia"));
        assertEquals(12_000 + 499, best, "the best conditional value plus the largest possible roll");
        int outside = value(generous, "potentia", new TestFuelEnvironment());
        assertTrue(outside >= 800 && outside < 800 + 500,
                "outside the End it is the base value plus a roll, never the End value: " + outside);
        var verdict = theflogat.technomancy.common.machines.CondenserBalance.verdict(200_000, best, SCALE);
        assertEquals(theflogat.technomancy.common.machines.CondenserBalance.Verdict.PERPETUAL_MOTION, verdict,
                "12499 x 80 x 0.25 = 249980 Q per unit, above the 200000 Q the condenser spends");
    }

    @Test
    void anAspectWithNoRowOfItsOwnIsWorthTheFallbackEverywhere() {
        EssentiaFuelTable table = shipped();
        assertEquals(table.fallback(), table.maxFuelValue(aspect("technom:not_in_the_table")));
    }

    // ---- the shipped table, row by row ----

    @Test
    void shippedTableCoversTheOriginalRowsAndNothingElse() {
        EssentiaFuelTable table = shipped();
        // 43 rows of the 1.7.10 table plus Terra, which it left out of every branch.
        assertEquals(44, table.listedAspects().size(), "listed aspects: " + table.listedAspects());
        assertEquals(25, table.fallback(), "the original ended getAspectFuel with a plain 'return 25'");
        // The four aspects the original genuinely never mentioned must stay on the fallback.
        for (String unlisted : List.of("gelum", "victus", "lucrum", "tutamen")) {
            assertFalse(table.listedAspects().contains(aspect(unlisted)), unlisted + " gained a row");
            assertEquals(25, value(table, unlisted, new TestFuelEnvironment()), unlisted);
        }
        // An aspect from another mod is also worth the fallback, not zero.
        assertEquals(25, table.fuelValue(new TestFuelEnvironment(), aspect("othermod:widgetum")));
        assertEquals(0, table.fuelValue(new TestFuelEnvironment(), null), "no aspect is no fuel");
    }

    @Test
    void unconditionalRowsMatchTheOriginal() {
        EssentiaFuelTable table = shipped();
        TestFuelEnvironment here = new TestFuelEnvironment();
        assertEquals(800, value(table, "ignis", here));
        assertEquals(800, value(table, "potentia", here));
        assertEquals(75, value(table, "aer", here));
        assertEquals(75, value(table, "ordo", here));
        assertEquals(75, value(table, "vitreus", here));
        assertEquals(75, value(table, "perditio", here));
        for (String tool : List.of("machina", "metallum", "motus", "instrumentum",
                "vinculum", "perfodio", "fabrico", "iter")) {
            assertEquals(75, value(table, tool, here), tool);
        }
        for (String creature : List.of("humanus", "sensus", "sano", "meto", "fames", "mortuus",
                "bestia", "venenum", "cognitio", "spiritus", "telum", "tempestas")) {
            assertEquals(30, value(table, creature, here), creature);
        }
        for (String plant : List.of("arbor", "herba", "messis", "pannus", "corpus")) {
            assertEquals(40, value(table, plant, here), plant);
        }
    }

    @Test
    void terraIsWorthTheSameAsTheOtherNonFirePrimals() {
        // Deliberate correction: Terra was the only primal with no branch of its own and so
        // fell through to the 25 fallback, a third of every other non-fire primal (defect A-15,
        // spec chapter 10 item 7).
        assertEquals(75, value(shipped(), "terra", new TestFuelEnvironment()));
    }

    @Test
    void aquaFollowsBiomeHumidity() {
        EssentiaFuelTable table = shipped();
        assertEquals(50, value(table, "aqua", new TestFuelEnvironment()));
        assertEquals(200, value(table, "aqua", new TestFuelEnvironment().in(TestFuelEnvironment.HUMID)));
    }

    @Test
    void magicAndEldritchTakeEitherTheEndOrAMagicalForest() {
        EssentiaFuelTable table = shipped();
        for (String name : List.of("praecantatio", "alienis")) {
            assertEquals(75, value(table, name, new TestFuelEnvironment()), name);
            assertEquals(300, value(table, name, new TestFuelEnvironment().dimension(TestFuelEnvironment.THE_END)), name);
            assertEquals(300, value(table, name,
                    new TestFuelEnvironment().in(TestFuelEnvironment.MAGICAL_FOREST)), name);
        }
    }

    @Test
    void undeadIsWorthMoreInTheEndAndNoLongerFallsThroughOutsideIt() {
        EssentiaFuelTable table = shipped();
        // The original's 'if' had no 'else', so outside the End Exanimis ran off the end of the
        // chain and was worth the 25 fallback (defect A-15, spec chapter 10 item 8).
        assertEquals(50, value(table, "exanimis", new TestFuelEnvironment()));
        assertEquals(100, value(table, "exanimis", new TestFuelEnvironment().dimension(TestFuelEnvironment.THE_END)));
    }

    @Test
    void voidIsWorthMoreBelowSeaLevelWhereverTheFloorIs() {
        EssentiaFuelTable table = shipped();
        // The original compared against an absolute 60, which in a world starting at -64 covers
        // nearly everything buildable rather than "deep underground" (defect A-17).
        assertEquals(50, value(table, "vacuos", new TestFuelEnvironment().at(63).seaLevel(63)));
        assertEquals(50, value(table, "vacuos", new TestFuelEnvironment().at(120).seaLevel(63)));
        assertEquals(200, value(table, "vacuos", new TestFuelEnvironment().at(62).seaLevel(63)));
        assertEquals(200, value(table, "vacuos", new TestFuelEnvironment().at(-40).seaLevel(63)));
        // A dimension with another sea level moves the boundary with it.
        assertEquals(200, value(table, "vacuos", new TestFuelEnvironment().at(30).seaLevel(32)));
        assertEquals(50, value(table, "vacuos", new TestFuelEnvironment().at(40).seaLevel(32)));
    }

    @Test
    void flightKeepsItsAbsoluteCeiling() {
        EssentiaFuelTable table = shipped();
        assertEquals(50, value(table, "volatus", new TestFuelEnvironment().at(150)));
        assertEquals(200, value(table, "volatus", new TestFuelEnvironment().at(151)));
        assertEquals(50, value(table, "volatus", new TestFuelEnvironment().at(-60)));
    }

    @Test
    void slimeLightDarknessAuraAndTaintFollowTheirConditions() {
        EssentiaFuelTable table = shipped();
        assertEquals(100, value(table, "limus", new TestFuelEnvironment().slimeChunk(false)));
        assertEquals(200, value(table, "limus", new TestFuelEnvironment().slimeChunk(true)));

        assertEquals(300, value(table, "lux", new TestFuelEnvironment().daytime(true)));
        assertEquals(50, value(table, "lux", new TestFuelEnvironment().daytime(false)));
        assertEquals(300, value(table, "tenebrae", new TestFuelEnvironment().daytime(false)));
        assertEquals(50, value(table, "tenebrae", new TestFuelEnvironment().daytime(true)));

        assertEquals(100, value(table, "auram", new TestFuelEnvironment()));
        assertEquals(600, value(table, "auram",
                new TestFuelEnvironment().in(TestFuelEnvironment.MAGICAL_FOREST)));
        assertEquals(100, value(table, "vitium", new TestFuelEnvironment()));
        assertEquals(600, value(table, "vitium", new TestFuelEnvironment().in(TestFuelEnvironment.TAINTED)));
    }

    @Test
    void exchangeRollsInTheCorrectedRangeAndNeverZero() {
        EssentiaFuelTable table = shipped();
        TestFuelEnvironment here = new TestFuelEnvironment();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int roll = 0; roll < 4000; roll++) {
            int rolled = value(table, "permutatio", here);
            lowest = Math.min(lowest, rolled);
            highest = Math.max(highest, rolled);
        }
        // The original was nextInt(1200), so it could return 0 and charge a unit of essentia for
        // nothing at all (defect A-16, spec chapter 10 item 9).
        assertTrue(lowest >= 200, "lowest roll was " + lowest + ", below the corrected floor of 200");
        assertTrue(highest <= 1199, "highest roll was " + highest + ", above the corrected ceiling");
        assertTrue(lowest < 250 && highest > 1150,
                "4000 rolls covered only " + lowest + ".." + highest + "; the range is not being used");
    }

    // ---- energy per unit ----

    @Test
    void energyPerUnitIsFuelValueTimesEightyTimesTheScale() {
        EssentiaFuelTable table = shipped();
        TestFuelEnvironment here = new TestFuelEnvironment();
        assertEquals(80L * 800, table.energyPerUnit(here, aspect("ignis"), 1.0),
                "a faithful 1:1 port makes one unit of ignis worth 64000 Q");
        assertEquals(16_000, table.energyPerUnit(here, aspect("ignis"), SCALE),
                "at the default scale coal breaks even: 4 units x 16000 Q = 64000 Q");
        assertEquals(1_500, table.energyPerUnit(here, aspect("aer"), SCALE));
        assertEquals(500, table.energyPerUnit(here, aspect("gelum"), SCALE), "the fallback row");
        assertEquals(0, table.energyPerUnit(here, null, SCALE));
        assertEquals(0, table.energyPerUnit(here, aspect("ignis"), 0.0), "scale 0 disables burning");

        // The formula itself, including the single rounding step.
        assertEquals(0, EssentiaFuelTable.energyPerUnit(0, SCALE));
        assertEquals(20, EssentiaFuelTable.energyPerUnit(1, SCALE));
        assertEquals(2, EssentiaFuelTable.energyPerUnit(1, 0.03), "80 x 0.03 = 2.4 rounds to 2");
        assertEquals(3, EssentiaFuelTable.energyPerUnit(1, 0.04), "80 x 0.04 = 3.2 rounds to 3");
    }

    // ---- codec behaviour ----

    @Test
    void firstMatchingConditionWins() {
        EssentiaFuelTable table = EssentiaFuelLoader.parse(Map.of(
                new ResourceLocation("technom", "order"), json("""
                {
                  "entries": [{
                    "aspects": ["ignis"],
                    "value": 1,
                    "when": [
                      { "condition": { "type": "technom:daytime", "day": true }, "value": 10 },
                      { "condition": { "type": "technom:height", "above": 0 }, "value": 20 }
                    ]
                  }]
                }""")));
        assertEquals(10, value(table, "ignis", new TestFuelEnvironment().daytime(true).at(64)),
                "both conditions hold, so the earlier one must win");
        assertEquals(20, value(table, "ignis", new TestFuelEnvironment().daytime(false).at(64)));
        assertEquals(1, value(table, "ignis", new TestFuelEnvironment().daytime(false).at(-10)));
    }

    @Test
    void laterFilesOverrideRowsAndReplaceClearsThem() {
        ResourceLocation base = new ResourceLocation("technom", "aaa_base");
        JsonElement baseFile = json(
                "{ \"fallback\": 7, \"entries\": [ { \"aspects\": [\"ignis\", \"aqua\"], \"value\": 100 } ] }");

        EssentiaFuelTable overridden = EssentiaFuelLoader.parse(Map.of(
                base, baseFile,
                new ResourceLocation("technom", "zzz_patch"),
                json("{ \"entries\": [ { \"aspects\": [\"ignis\"], \"value\": 5 } ] }")));
        TestFuelEnvironment here = new TestFuelEnvironment();
        assertEquals(5, value(overridden, "ignis", here), "the later file wins for the aspect it names");
        assertEquals(100, value(overridden, "aqua", here), "and leaves the rest of that row alone");
        assertEquals(7, overridden.fallback(), "a file without a fallback keeps the previous one");

        EssentiaFuelTable replaced = EssentiaFuelLoader.parse(Map.of(
                base, baseFile,
                new ResourceLocation("technom", "zzz_patch"),
                json("{ \"replace\": true, \"entries\": [] }")));
        assertTrue(replaced.isEmpty(), "replace must be able to empty the table, not just override it");
        assertEquals(0, replaced.fallback());
    }

    @Test
    void aDataPackOverridesTheShippedTableWhateverItsNamespaceSortsAs() {
        // "aaa_pack" sorts before "technom", so a plain alphabetical merge would apply the pack
        // first and then overwrite it with our own defaults - a silent revert that reads as a
        // balance change rather than a mistake.
        EssentiaFuelTable table = EssentiaFuelLoader.parse(Map.of(
                new ResourceLocation("technom", "default"), read(SHIPPED),
                new ResourceLocation("aaa_pack", "tweaks"),
                json("{ \"fallback\": 1, \"entries\": [ { \"aspects\": [\"ignis\"], \"value\": 7 } ] }")));
        TestFuelEnvironment here = new TestFuelEnvironment();
        assertEquals(7, value(table, "ignis", here), "the data pack did not win");
        assertEquals(1, table.fallback(), "the data pack's fallback did not win");
        assertEquals(800, value(table, "potentia", here),
                "overriding one aspect of a shared row must not disturb the others");
        assertEquals(75, value(table, "terra", here), "the rest of the shipped table is still there");
    }

    @Test
    void badFilesAreSkippedWithoutTakingTheTableDown() {
        ResourceLocation good = new ResourceLocation("technom", "aaa_good");
        EssentiaFuelTable table = EssentiaFuelLoader.parse(Map.of(
                good, json("{ \"fallback\": 3, \"entries\": [ { \"aspects\": [\"ignis\"], \"value\": 9 } ] }"),
                new ResourceLocation("technom", "unknown_condition"), json("""
                        { "entries": [ { "aspects": ["aqua"], "value": 1, "when": [
                            { "condition": { "type": "technom:no_such_condition" }, "value": 2 } ] } ] }"""),
                new ResourceLocation("technom", "negative"),
                json("{ \"entries\": [ { \"aspects\": [\"ordo\"], \"value\": -1 } ] }"),
                new ResourceLocation("technom", "no_aspects"),
                json("{ \"entries\": [ { \"value\": 1 } ] }")));
        assertEquals(9, value(table, "ignis", new TestFuelEnvironment()), "the valid file still applied");
        assertEquals(3, value(table, "aqua", new TestFuelEnvironment()), "the broken row did not apply");
        assertEquals(3, value(table, "ordo", new TestFuelEnvironment()));
        assertEquals(1, table.listedAspects().size(), "listed: " + table.listedAspects());
    }

    @Test
    void anEmptyTableIsTheStartingState() {
        assertSame(EssentiaFuelTable.EMPTY, EssentiaFuelLoader.table(),
                "nothing must burn before a data pack has been read");
        assertTrue(EssentiaFuelTable.EMPTY.isEmpty());
        assertEquals(0, EssentiaFuelTable.EMPTY.fuelValue(new TestFuelEnvironment(), aspect("ignis")));
    }
}
