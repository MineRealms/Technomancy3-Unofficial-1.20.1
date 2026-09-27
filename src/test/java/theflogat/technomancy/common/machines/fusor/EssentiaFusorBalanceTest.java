package theflogat.technomancy.common.machines.fusor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;

/**
 * The one thing the fusor could break: energy conservation against the dynamo.
 *
 * <p>A fusion makes one unit of essentia, which a dynamo can burn for
 * {@code maxFuelValue x 80 x scale} Q. If the fusion cost less than that, "two cheap primals into
 * one expensive compound" would be a renewable energy profit. These assertions are made against
 * the shipped fuel table, so a change to the data has to keep the invariant or fail here.</p>
 */
class EssentiaFusorBalanceTest {

    private static final String SHIPPED = "/data/technom/technomancy/essentia_fuel/default.json";
    /** The project default. */
    private static final double SCALE = 0.25;

    private static EssentiaFuelTable shipped() {
        try (InputStream stream = EssentiaFusorBalanceTest.class.getResourceAsStream(SHIPPED)) {
            assertNotNull(stream, "missing shipped resource " + SHIPPED);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return EssentiaFuelLoader.parse(Map.of(new ResourceLocation("technom", "default"), json));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void noFusionCanEverNetEnergy() {
        EssentiaFuelTable table = shipped();
        for (double scale : new double[] {0.0, 0.25, 1.0, 4.0, 16.0}) {
            for (AspectId output : table.listedAspects()) {
                long cost = EssentiaFusorBalance.costQ(table, scale, output);
                long refund = EssentiaFuelTable.energyPerUnit(table.maxFuelValue(output), scale);
                assertTrue(cost >= refund, "at scale " + scale + " fusing " + output + " costs " + cost
                        + " but a dynamo pays back up to " + refund);
            }
            // Unlisted aspects fall back, and the fallback must be covered too.
            long fallback = EssentiaFuelTable.energyPerUnit(table.fallback(), scale);
            assertTrue(EssentiaFusorBalance.costQ(table, scale, AspectId.parse("humanus")) >= fallback);
        }
    }

    @Test
    void theWorstCaseFitsInTheBuffer() {
        EssentiaFuelTable table = shipped();
        long worstDefault = EssentiaFusorBalance.worstCaseCostQ(table, SCALE);
        assertEquals(23_980, worstDefault, "permutatio at 200 + 999 random, times 80, times 0.25");
        assertTrue(worstDefault <= EssentiaFusorBalance.ENERGY_CAPACITY_Q,
                "the fusor could never afford its own worst case");
        // The config allows a fuel scale up to 16, and the buffer has to hold one fusion there too.
        assertTrue(EssentiaFusorBalance.worstCaseCostQ(table, 16.0)
                <= EssentiaFusorBalance.ENERGY_CAPACITY_Q,
                "at the maximum fuel scale the fusor cannot buffer one fusion");
    }

    @Test
    void theLegacyFlatCostIsTheFloor() {
        EssentiaFuelTable table = shipped();
        assertEquals(EssentiaFusorBalance.LEGACY_COST_Q,
                EssentiaFusorBalance.costQ(table, 0.0, AspectId.parse("potentia")),
                "with essentia worth no energy at all, the original's 1000 FE still applies");
        assertEquals(EssentiaFusorBalance.LEGACY_COST_Q, EssentiaFusorBalance.costQ(table, SCALE, null),
                "an unconfigured fusor quotes the floor");
        // potentia is 800 fuel points, so 800 x 80 x 0.25 = 16000 Q.
        assertEquals(16_000, EssentiaFusorBalance.costQ(table, SCALE, AspectId.parse("potentia")));
    }
}
