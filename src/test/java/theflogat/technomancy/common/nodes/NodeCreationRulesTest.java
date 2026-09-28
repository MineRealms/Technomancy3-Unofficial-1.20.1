package theflogat.technomancy.common.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The world-free node-creation rules, transcribed from {@code TileNodeGenerator.generateNode}. */
class NodeCreationRulesTest {

    @Test
    void theDedicatedAspectFollowsTheFacing() {
        assertSame(NodeCreationRules.AURAM, NodeCreationRules.dedicatedAspect(Direction.NORTH));
        assertSame(NodeCreationRules.AURAM, NodeCreationRules.dedicatedAspect(Direction.WEST));
        assertSame(NodeCreationRules.VITIUM, NodeCreationRules.dedicatedAspect(Direction.SOUTH));
        assertSame(NodeCreationRules.VITIUM, NodeCreationRules.dedicatedAspect(Direction.EAST));
    }

    @Test
    void energyIsTheHalfTotalSquaredTimesTheOriginalFactor() {
        // ((200 + 200) / 2)^2 * 762.939453125 = 30517578.125, rounded.
        assertEquals(30_517_578L, NodeCreationRules.energyCost(200, 200));
        assertEquals(0L, NodeCreationRules.energyCost(0, 0));
        // A one-unit asymmetry still divides the total in half before squaring.
        assertEquals(30_517_578L, NodeCreationRules.energyCost(201, 199));
    }

    @Test
    void nodeVisIsTheHalfTotal() {
        assertEquals(200, NodeCreationRules.nodeVis(200, 200));
        assertEquals(5, NodeCreationRules.nodeVis(3, 8));
    }

    @Test
    void theTypeCascadeMatchesTheOriginal() {
        // aurum == taint and the sum is one of the four pure totals
        assertSame(NodeTypeId.PURE, NodeCreationRules.type(61, 61));
        assertSame(NodeTypeId.PURE, NodeCreationRules.type(255, 255));
        // over the total ceiling is hungry
        assertSame(NodeTypeId.HUNGRY, NodeCreationRules.type(200, 200));
        // far more aurum than taint is unstable
        assertSame(NodeTypeId.UNSTABLE, NodeCreationRules.type(150, 50));
        // far more taint than aurum is tainted, or dark when the taint is small
        assertSame(NodeTypeId.TAINTED, NodeCreationRules.type(20, 120));
        assertSame(NodeTypeId.DARK, NodeCreationRules.type(10, 80));
        // an even split is ordinary
        assertSame(NodeTypeId.NORMAL, NodeCreationRules.type(50, 50));
    }

    @Test
    void theModifierCascadeMatchesTheOriginal() {
        assertSame(NodeModifierId.FADING, NodeCreationRules.modifier(30, 30));
        assertSame(NodeModifierId.BRIGHT, NodeCreationRules.modifier(120, 100));
        assertSame(NodeModifierId.PALE, NodeCreationRules.modifier(200, 200));
        assertSame(NodeModifierId.NONE, NodeCreationRules.modifier(100, 100));
    }
}
