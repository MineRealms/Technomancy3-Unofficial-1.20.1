package theflogat.technomancy.common.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NodeFusionTest {

    private static final AspectId AER = AspectId.parse("aer");
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId TERRA = AspectId.parse("terra");

    private static NodeVis vis(Object... pairs) {
        LinkedHashMap<AspectId, Integer> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((AspectId) pairs[index], (Integer) pairs[index + 1]);
        }
        return new NodeVis(map);
    }

    private static AuraNodeState node(NodeVis base, NodeVis current) {
        return new AuraNodeState(Optional.of(UUID.nameUUIDFromBytes(base.toString().getBytes())),
                NodeTypeId.NORMAL, NodeModifierId.NONE, base, current);
    }

    private static int total(AuraNodeState state, AspectId aspect, boolean base) {
        return (base ? state.baseVis() : state.currentVis()).amount(aspect);
    }

    @Test
    void everythingMovesAndNothingIsCreatedOrLost() {
        AuraNodeState source = node(vis(AER, 20, IGNIS, 5), vis(AER, 12, IGNIS, 5));
        AuraNodeState destination = node(vis(TERRA, 30), vis(TERRA, 10));
        NodeFusion.Result result = NodeFusion.fuse(source, destination);

        for (AspectId aspect : new AspectId[] {AER, IGNIS, TERRA}) {
            assertEquals(total(source, aspect, true) + total(destination, aspect, true),
                    total(result.source(), aspect, true) + total(result.destination(), aspect, true),
                    "base vis of " + aspect);
            assertEquals(total(source, aspect, false) + total(destination, aspect, false),
                    total(result.source(), aspect, false) + total(result.destination(), aspect, false),
                    "live vis of " + aspect);
        }
        assertEquals(25, result.movedBase());
        assertEquals(17, result.movedVis());
        assertTrue(result.sourceEmptied(), "a fully drained source must be recognised as empty");
        assertEquals(20, total(result.destination(), AER, true));
        assertEquals(12, total(result.destination(), AER, false));
        assertEquals(30, total(result.destination(), TERRA, true));
    }

    /** Identity, type and modifier are the destination's; only vis moves. */
    @Test
    void identityIsNotTransferred() {
        AuraNodeState source = node(vis(AER, 4), vis(AER, 4)).withType(NodeTypeId.parse("hungry"));
        AuraNodeState destination = node(vis(TERRA, 4), vis(TERRA, 4));
        NodeFusion.Result result = NodeFusion.fuse(source, destination);
        assertEquals(destination.instanceId(), result.destination().instanceId());
        assertEquals(destination.type(), result.destination().type());
        assertEquals(source.instanceId(), result.source().instanceId());
    }

    /** A destination at the cap takes what it can; the rest stays in the source. */
    @Test
    void aFullDestinationTakesOnlyWhatFits() {
        AuraNodeState source = node(vis(AER, 10), vis(AER, 10));
        AuraNodeState destination = node(vis(AER, NodeFusion.MAX_BASE_VIS - 4), vis(AER, 0));
        NodeFusion.Result result = NodeFusion.fuse(source, destination);

        assertEquals(4, result.movedBase(), "only four points of base fit under the cap");
        // The live vis is bounded by the destination's new base, not by the base that moved with
        // it, so a destination with room for it takes all ten.
        assertEquals(10, result.movedVis());
        assertEquals(6, total(result.source(), AER, true), "the base remainder stays in the source");
        assertEquals(0, total(result.source(), AER, false));
        assertEquals(NodeFusion.MAX_BASE_VIS, total(result.destination(), AER, true));
        assertFalse(result.sourceEmptied(), "a source that still holds base vis must not be removed");
    }

    /** Live vis never overshoots the base it lands in. */
    @Test
    void liveVisIsBoundedByTheBaseItMovesInto() {
        AuraNodeState source = node(vis(AER, 0), vis(AER, 8));
        AuraNodeState destination = node(vis(AER, 5), vis(AER, 5));
        NodeFusion.Result result = NodeFusion.fuse(source, destination);
        assertEquals(0, result.movedBase());
        assertEquals(0, result.movedVis(), "a destination already at its base takes nothing");
        assertFalse(result.moved());
    }

    @Test
    void anEmptySourceMovesNothing() {
        AuraNodeState source = node(NodeVis.EMPTY, NodeVis.EMPTY);
        AuraNodeState destination = node(vis(TERRA, 10), vis(TERRA, 2));
        NodeFusion.Result result = NodeFusion.fuse(source, destination);
        assertFalse(result.moved());
        assertEquals(destination, result.destination());
    }
}
