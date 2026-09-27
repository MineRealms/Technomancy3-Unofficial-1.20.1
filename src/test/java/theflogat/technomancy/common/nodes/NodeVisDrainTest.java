package theflogat.technomancy.common.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;

class NodeVisDrainTest {

    private static final AspectId AER = AspectId.parse("aer");
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId TERRA = AspectId.parse("terra");

    private static NodeVis vis(Object... pairs) {
        LinkedHashMap<AspectId, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((AspectId) pairs[i], (Integer) pairs[i + 1]);
        }
        return new NodeVis(map);
    }

    private static int total(NodeVis vis) {
        return vis.amounts().values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void neverTakesTheLastVisOfAnAspect() {
        assertTrue(NodeVisDrain.pick(vis(AER, 1, IGNIS, 0, TERRA, 1), RandomSource.create(1)).isEmpty());
        assertTrue(NodeVisDrain.pick(NodeVis.EMPTY, RandomSource.create(1)).isEmpty());
        for (long seed = 0; seed < 200; seed++) {
            NodeVisDrain.Drain drain = NodeVisDrain.pick(vis(AER, 1, IGNIS, 5, TERRA, 0), RandomSource.create(seed)).orElseThrow();
            assertEquals(IGNIS, drain.aspect(), "only ignis is above the floor");
            assertEquals(4, drain.after().amount(IGNIS));
        }
    }

    @Test
    void takesExactlyOneVisAndKeepsEntryOrder() {
        NodeVis before = vis(AER, 7, IGNIS, 3, TERRA, 0);
        for (long seed = 0; seed < 200; seed++) {
            NodeVisDrain.Drain drain = NodeVisDrain.pick(before, RandomSource.create(seed)).orElseThrow();
            assertEquals(total(before) - 1, total(drain.after()));
            assertEquals(before.amount(drain.aspect()) - 1, drain.after().amount(drain.aspect()));
            // Zero entries stay: TC4R's erosion lifecycle depends on them, and NodeVis equality is ordered.
            assertEquals(List.copyOf(before.amounts().keySet()), List.copyOf(drain.after().amounts().keySet()));
        }
    }

    @Test
    void choosesAmongEligibleAspectsUniformlyEnough() {
        NodeVis before = vis(AER, 50, IGNIS, 50);
        Map<AspectId, Integer> seen = new LinkedHashMap<>();
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 2000; i++) {
            seen.merge(NodeVisDrain.pick(before, random).orElseThrow().aspect(), 1, Integer::sum);
        }
        assertTrue(seen.getOrDefault(AER, 0) > 800 && seen.getOrDefault(IGNIS, 0) > 800, seen.toString());
    }

    /**
     * Node Vis keeps its 1.7.10 value relative to essentia at any fuel scale: 30 fuel points
     * against ignis/potentia's 800, so it can never out-earn the essentia dynamo per unit.
     */
    @Test
    void visIsWorthThirtyFuelPointsAtTheSharedScale() {
        assertEquals(2400, EssentiaFuelTable.energyPerUnit(NodeDynamoBlockEntity.VIS_FUEL_POINTS, 1.0));
        assertEquals(600, EssentiaFuelTable.energyPerUnit(NodeDynamoBlockEntity.VIS_FUEL_POINTS, 0.25));
        assertTrue(EssentiaFuelTable.energyPerUnit(NodeDynamoBlockEntity.VIS_FUEL_POINTS, 0.25)
                < EssentiaFuelTable.energyPerUnit(800, 0.25));
    }
}
