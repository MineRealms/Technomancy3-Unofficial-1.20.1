package theflogat.technomancy.common.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.nodes.NodeFabricatorWork.Operation;
import theflogat.technomancy.common.nodes.NodeFabricatorWork.Plan;

class NodeFabricatorWorkTest {

    @Test
    void rechargeNeedsEnergyEssentiaAndARoomToPutItIn() {
        Plan plan = NodeFabricatorWork.plan(false, 1000, 1, true, 4, 5);
        assertSame(Operation.RECHARGE, plan.operation());
        assertEquals(NodeFabricatorWork.RECHARGE_ENERGY, plan.energy());
        assertEquals(1, plan.essentia());

        assertSame(Operation.NONE, NodeFabricatorWork.plan(false, 999, 1, true, 4, 5).operation(),
                "one Q short must not work");
        assertSame(Operation.NONE, NodeFabricatorWork.plan(false, 1000, 0, true, 4, 5).operation(),
                "no essentia must not work");
        assertSame(Operation.NONE, NodeFabricatorWork.plan(false, 1000, 1, true, 5, 5).operation(),
                "a full aspect is not recharged");
        assertSame(Operation.NONE, NodeFabricatorWork.plan(false, 1000, 1, false, 0, 5).operation(),
                "an aspect the node does not list is not recharged without a gem");
    }

    @Test
    void expansionNeedsAGemTenUnitsAndTenThousand() {
        assertSame(Operation.NONE, NodeFabricatorWork.plan(false, 10_000, 10, true, 5, 5).operation(),
                "no gem, no expansion");
        Plan plan = NodeFabricatorWork.plan(true, 10_000, 10, true, 5, 5);
        assertSame(Operation.EXPAND, plan.operation());
        assertEquals(NodeFabricatorWork.EXPAND_ENERGY, plan.energy());
        assertEquals(NodeFabricatorWork.EXPAND_ESSENTIA, plan.essentia());

        assertSame(Operation.NONE, NodeFabricatorWork.plan(true, 9999, 10, true, 5, 5).operation());
        assertSame(Operation.NONE, NodeFabricatorWork.plan(true, 10_000, 9, true, 5, 5).operation());
        assertSame(Operation.NONE,
                NodeFabricatorWork.plan(true, 10_000, 10, true, NodeFabricatorWork.MAX_BASE_VIS,
                        NodeFabricatorWork.MAX_BASE_VIS).operation(),
                "the base vis cap is respected");
    }

    /** A gem must never stop the cheap job: recharge is checked first, as in the original. */
    @Test
    void rechargeWinsOverExpansionWhenBothAreAffordable() {
        Plan plan = NodeFabricatorWork.plan(true, 1_000_000, 64, true, 3, 5);
        assertSame(Operation.RECHARGE, plan.operation());
    }

    /** With a gem, an aspect the node never listed is introduced through expansion. */
    @Test
    void expansionIntroducesANewAspect() {
        Plan plan = NodeFabricatorWork.plan(true, 10_000, 10, false, 0, 0);
        assertSame(Operation.EXPAND, plan.operation());
        assertFalse(Plan.NOTHING.works());
        assertTrue(plan.works());
    }
}
