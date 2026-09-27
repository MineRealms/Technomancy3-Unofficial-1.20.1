package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;

/**
 * Node dynamo against a real TC4R aura node. Written for the merged final run; not yet executed.
 * Uses TC4R internals ({@code TCBlocks}, {@code AuraNodeBlockEntity}) only to set the scene.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NodeDynamoGameTests {

    private static final String BATCH = "technom_node_dynamo";
    private static final AspectId AER = AspectId.parse("aer");
    private static final AspectId IGNIS = AspectId.parse("ignis");

    private NodeDynamoGameTests() {}

    private static AuraNodeBlockEntity placeNode(GameTestHelper helper, BlockPos rel, int aer, int ignis) {
        helper.setBlock(rel, TCBlocks.AURA_NODE.get());
        AuraNodeBlockEntity node = (AuraNodeBlockEntity) helper.getBlockEntity(rel);
        LinkedHashMap<AspectId, Integer> vis = new LinkedHashMap<>();
        vis.put(AER, aer);
        vis.put(IGNIS, ignis);
        // Fading: recharge interval 0, so the node cannot refill itself during the test.
        node.setNodeState(new AuraNodeState(Optional.of(UUID.randomUUID()), NodeTypeId.NORMAL,
                NodeModifierId.FADING, new NodeVis(vis), new NodeVis(vis)));
        return node;
    }

    private static NodeDynamoBlockEntity placeDynamo(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, TechnomBlocks.NODE_DYNAMO.get());
        return (NodeDynamoBlockEntity) helper.getBlockEntity(rel);
    }

    /** Takes whole Vis until each aspect is at the floor of one, and converts every one of them. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void drainsToTheFloorAndConservesEveryVis(GameTestHelper helper) {
        AuraNodeBlockEntity node = placeNode(helper, new BlockPos(2, 3, 2), 3, 1);
        NodeDynamoBlockEntity dynamo = placeDynamo(helper, new BlockPos(2, 1, 2));
        helper.runAfterDelay(150, () -> {
            NodeVis left = node.nodeState().currentVis();
            helper.assertTrue(left.amount(AER) == 1 && left.amount(IGNIS) == 1,
                    "node should sit at the floor of one per aspect: " + left.amounts());
            long perVis = NodeDynamoBlockEntity.energyPerVis();
            long accounted = dynamo.energy().ledger().stored() + dynamo.fuel() + dynamo.vis() * perVis;
            helper.assertTrue(accounted == 2 * perVis,
                    "two Vis taken must be worth exactly " + 2 * perVis + " Q, found " + accounted);
            helper.succeed();
        });
    }

    /** A dynamo switched off by redstone leaves the node alone (the original drained anyway). */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 120)
    public static void redstoneOffDoesNotDrain(GameTestHelper helper) {
        AuraNodeBlockEntity node = placeNode(helper, new BlockPos(2, 3, 2), 10, 10);
        NodeDynamoBlockEntity dynamo = placeDynamo(helper, new BlockPos(2, 1, 2));
        dynamo.redstone().set(RedstoneMode.HIGH);
        helper.runAfterDelay(80, () -> {
            NodeVis left = node.nodeState().currentVis();
            helper.assertTrue(left.amount(AER) == 10 && left.amount(IGNIS) == 10,
                    "an unpowered HIGH-mode dynamo drained the node: " + left.amounts());
            helper.assertTrue(dynamo.vis() == 0, "vis buffered while off");
            helper.succeed();
        });
    }
}
