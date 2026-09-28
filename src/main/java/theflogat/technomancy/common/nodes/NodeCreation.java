package theflogat.technomancy.common.nodes;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@code ThaumcraftWorldGenerator.createNodeAt} equivalent the node fabricator needs.
 *
 * <p>TC4R has no public create-node call, but its own {@code AuraNodeBlock.setPlacedBy} creates a
 * node by placing {@code TCBlocks.AURA_NODE} and calling {@code AuraNodeBlockEntity#setNodeState}.
 * That is exactly what this does, so no Mixin or accessor is needed: the primitive is already the
 * supported way the raw node block turns into a real node.</p>
 */
public final class NodeCreation {

    private NodeCreation() {
    }

    /**
     * Places a fresh aura node at {@code pos} if the space is free, with one aspect at
     * {@code amount} Vis and base Vis.
     *
     * @return whether the node was created
     */
    public static boolean create(ServerLevel level, BlockPos pos, @Nullable AspectId aspect,
            int amount, NodeTypeId type, NodeModifierId modifier) {
        if (aspect == null || amount <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        level.setBlockAndUpdate(pos, TCBlocks.AURA_NODE.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
            return false;
        }
        NodeVis vis = new NodeVis(Map.of(aspect, amount));
        node.setNodeState(new AuraNodeState(Optional.of(UUID.randomUUID()), type, modifier, vis, vis));
        return true;
    }
}
