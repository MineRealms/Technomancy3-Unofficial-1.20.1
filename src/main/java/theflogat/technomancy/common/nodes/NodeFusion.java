package theflogat.technomancy.common.nodes;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Moving one node's Vis into another: the arithmetic of the fusion focus, without a world.
 *
 * <p>{@code ItemFusionFocus} broke a node, kept its aspects on the item and rebuilt a node
 * somewhere else out of them. A new node cannot be built through TC4R's public node interface, so
 * the port fuses a source node <em>into a second existing node</em> instead: the destination
 * inherits both the source's base (its capacity) and its live Vis, and the source is left with
 * whatever did not fit.</p>
 *
 * <p>Nothing is created or destroyed here: for every aspect,
 * {@code source + destination} is the same before and after, both for base and for live Vis. That
 * is the property the tests assert, and it is what the 1.7.10 focus failed at - it never cleared
 * the aspects it had picked up, so every further right-click made another copy of the same
 * node.</p>
 */
public final class NodeFusion {

    /** Same ceiling per aspect as the fabricator's expansion. */
    public static final int MAX_BASE_VIS = NodeFabricatorWork.MAX_BASE_VIS;

    private NodeFusion() {
    }

    /**
     * @param source      what is left of the source node
     * @param destination the destination node after the transfer
     * @param movedBase   base Vis moved, summed over aspects
     * @param movedVis    live Vis moved, summed over aspects
     */
    public record Result(AuraNodeState source, AuraNodeState destination, int movedBase, int movedVis) {

        /** Whether the source has nothing left at all, so its block can be taken away. */
        public boolean sourceEmptied() {
            return total(source.baseVis()) == 0 && total(source.currentVis()) == 0;
        }

        public boolean moved() {
            return movedBase > 0 || movedVis > 0;
        }
    }

    public static int total(NodeVis vis) {
        int sum = 0;
        for (int value : vis.amounts().values()) {
            sum += value;
        }
        return sum;
    }

    /**
     * Fuses {@code source} into {@code destination}.
     *
     * <p>Per aspect the destination's base is raised by the source's base, clamped at
     * {@link #MAX_BASE_VIS}, and its live Vis by as much of the source's as the new base leaves
     * room for. Whatever does not fit stays in the source, which is why a fusion into an almost
     * full node is a partial transfer rather than a loss.</p>
     *
     * <p>Node type, modifier and identity are the destination's; only Vis moves.</p>
     */
    public static Result fuse(AuraNodeState source, AuraNodeState destination) {
        Set<AspectId> aspects = new LinkedHashSet<>(destination.baseVis().amounts().keySet());
        aspects.addAll(destination.currentVis().amounts().keySet());
        aspects.addAll(source.baseVis().amounts().keySet());
        aspects.addAll(source.currentVis().amounts().keySet());

        LinkedHashMap<AspectId, Integer> destBase = new LinkedHashMap<>(destination.baseVis().amounts());
        LinkedHashMap<AspectId, Integer> destVis = new LinkedHashMap<>(destination.currentVis().amounts());
        LinkedHashMap<AspectId, Integer> srcBase = new LinkedHashMap<>(source.baseVis().amounts());
        LinkedHashMap<AspectId, Integer> srcVis = new LinkedHashMap<>(source.currentVis().amounts());
        int movedBase = 0;
        int movedVis = 0;

        for (AspectId aspect : aspects) {
            int fromBase = srcBase.getOrDefault(aspect, 0);
            int intoBase = destBase.getOrDefault(aspect, 0);
            int base = Math.min(fromBase, MAX_BASE_VIS - intoBase);
            if (base > 0) {
                destBase.put(aspect, intoBase + base);
                srcBase.put(aspect, fromBase - base);
                movedBase += base;
            }
            // Live Vis can never exceed the base it now has room in; the rest waits in the source.
            int newBase = destBase.getOrDefault(aspect, 0);
            int fromVis = srcVis.getOrDefault(aspect, 0);
            int intoVis = destVis.getOrDefault(aspect, 0);
            int vis = Math.min(fromVis, Math.max(0, newBase - intoVis));
            if (vis > 0) {
                destVis.put(aspect, intoVis + vis);
                srcVis.put(aspect, fromVis - vis);
                movedVis += vis;
            }
        }
        return new Result(
                source.withVis(new NodeVis(prune(srcBase)), new NodeVis(prune(srcVis))),
                destination.withVis(new NodeVis(destBase), new NodeVis(destVis)),
                movedBase, movedVis);
    }

    /**
     * Drops emptied entries from the <em>source</em> only.
     *
     * <p>TC4R keeps zero entries on a node because its erosion lifecycle needs them, which is
     * why the destination map is left exactly as it is. A source that gave everything away is on
     * its way out entirely, so keeping a row of zeros there would only stop
     * {@link Result#sourceEmptied()} from ever being true.</p>
     */
    private static Map<AspectId, Integer> prune(Map<AspectId, Integer> amounts) {
        LinkedHashMap<AspectId, Integer> result = new LinkedHashMap<>();
        amounts.forEach((aspect, amount) -> {
            if (amount > 0) {
                result.put(aspect, amount);
            }
        });
        return result;
    }
}
