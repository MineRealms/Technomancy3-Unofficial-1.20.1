package theflogat.technomancy.common.nodes;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * Picks the one whole Vis a node dynamo takes from a node, and builds the node's next Vis store.
 *
 * <p>World-free so the rules are testable: {@code TileNodeDynamo.takeAspectsFromNodes}
 * (TileNodeDynamo.java:45-71) shuffled the node's aspects and took one Vis from the first aspect
 * holding more than one. The "more than one" floor is kept on purpose: TC4R retains exhausted
 * aspects at zero and lets them erode out of the node's base, so draining to zero would slowly
 * destroy the node instead of merely emptying it.</p>
 */
public final class NodeVisDrain {

    /** An aspect is only drained while it holds more than this. */
    public static final int FLOOR = 1;

    private NodeVisDrain() {
    }

    /** One Vis of {@code aspect} taken; {@code after} is the node's complete new store. */
    public record Drain(AspectId aspect, NodeVis after) {}

    /**
     * Chooses uniformly among the aspects above the floor and returns the store with one Vis less.
     * Entry order is preserved, because {@link NodeVis#equals} is order-sensitive and TC4R's
     * compare-and-set would otherwise see an unrelated change.
     */
    public static Optional<Drain> pick(NodeVis vis, RandomSource random) {
        List<AspectId> candidates = new ArrayList<>();
        for (Map.Entry<AspectId, Integer> entry : vis.amounts().entrySet()) {
            if (entry.getValue() > FLOOR) {
                candidates.add(entry.getKey());
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        AspectId chosen = candidates.get(random.nextInt(candidates.size()));
        LinkedHashMap<AspectId, Integer> next = new LinkedHashMap<>(vis.amounts());
        next.merge(chosen, -1, Integer::sum);
        return Optional.of(new Drain(chosen, new NodeVis(next)));
    }
}
