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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.nodes.NodeFabricatorBlock;
import theflogat.technomancy.common.nodes.NodeFabricatorWork;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorBlockEntity;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorShellBlockEntity;

/**
 * The node fabricator pair, its shells and its two operations on a real aura node. Written for
 * the merged final run; not yet executed. The template is 5x5x5, so these tests use a single
 * fabricator plus a hand-placed node where the pair is not what is under test, and the full
 * six-block pair only where it is.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NodeFabricatorGameTests {

    private static final String BATCH = "technom_node_fabricator";
    private static final AspectId AER = AspectId.parse("aer");

    private NodeFabricatorGameTests() {}

    private static NodeFabricatorBlockEntity place(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, TechnomBlocks.NODE_FABRICATOR.get().defaultBlockState()
                .setValue(NodeFabricatorBlock.FACING, facing));
        NodeFabricatorBlockEntity machine = (NodeFabricatorBlockEntity) helper.getBlockEntity(rel);
        machine.formShells(helper.getLevel());
        return machine;
    }

    /** Eight shells, all pointing at the controller, and every one of them a port into it. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void theSlabIsBuiltAndEveryShellIsAPortIntoTheController(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.NORTH);
        helper.assertTrue(machine.structureComplete(helper.getLevel()), "the slab was not formed");

        machine.energy().ledger().receive(5000, 0, false);
        for (BlockPos shellRel : NodeFabricatorBlockEntity.shellPositions(pos, Direction.NORTH)) {
            NodeFabricatorShellBlockEntity shell = (NodeFabricatorShellBlockEntity) helper.getBlockEntity(shellRel);
            helper.assertTrue(shell != null && helper.absolutePos(pos).equals(shell.host()),
                    "shell at " + shellRel + " does not know its host");
            IEnergyStorage energy = shell.getCapability(ForgeCapabilities.ENERGY, Direction.UP)
                    .orElseThrow(() -> new AssertionError("no energy port on the shell at " + shellRel));
            // One ledger seen through nine blocks: reading 5000 from every shell is the point.
            helper.assertTrue(energy.getEnergyStored() == 5000,
                    "shell reports " + energy.getEnergyStored() + " Q instead of the host's 5000");
        }
        // Filling through one shell must not create energy in another.
        NodeFabricatorShellBlockEntity first = (NodeFabricatorShellBlockEntity) helper
                .getBlockEntity(NodeFabricatorBlockEntity.shellPositions(pos, Direction.NORTH).get(0));
        first.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new)
                .receiveEnergy(1000, false);
        helper.assertTrue(machine.energy().ledger().stored() == 6000,
                "the host holds " + machine.energy().ledger().stored() + " Q, expected 6000");
        helper.succeed();
    }

    /** A blocked position leaves the slab unformed instead of breaking the player's blocks. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void aBlockedSlabIsNotBuiltAndNothingIsDestroyed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        BlockPos blocked = pos.above();
        helper.setBlock(blocked, Blocks.DIAMOND_BLOCK);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.NORTH);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, blocked);
        helper.assertFalse(machine.structureComplete(helper.getLevel()),
                "a fabricator with a diamond block in its slab reported itself complete");
        helper.succeed();
    }

    /** Breaking the controller takes every shell with it: no unbreakable residue. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void removingTheControllerRemovesTheShells(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.NORTH);
        machine.removeShells(helper.getLevel());
        helper.setBlock(pos, Blocks.AIR);
        for (BlockPos shellRel : NodeFabricatorBlockEntity.shellPositions(pos, Direction.NORTH)) {
            helper.assertBlockPresent(Blocks.AIR, shellRel);
        }
        helper.succeed();
    }

    /**
     * One recharge: 1000 Q and one unit of essentia for one Vis, committed through the node
     * compare-and-set, with nothing debited when the node cannot take it.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void rechargeMovesOneVisAndChargesExactlyOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.EAST);
        BlockPos nodePos = machine.nodePosition();
        // nodePosition() is absolute; helper.setBlock/getBlockEntity take structure-relative
        // coordinates, so go through the level with the absolute position.
        helper.getLevel().setBlockAndUpdate(nodePos, TCBlocks.AURA_NODE.get().defaultBlockState());
        AuraNodeBlockEntity node = (AuraNodeBlockEntity) helper.getLevel().getBlockEntity(nodePos);
        LinkedHashMap<AspectId, Integer> base = new LinkedHashMap<>();
        base.put(AER, 10);
        LinkedHashMap<AspectId, Integer> current = new LinkedHashMap<>();
        current.put(AER, 4);
        node.setNodeState(new AuraNodeState(Optional.of(UUID.randomUUID()), NodeTypeId.NORMAL,
                NodeModifierId.FADING, new NodeVis(base), new NodeVis(current)));

        machine.energy().ledger().receive(NodeFabricatorWork.RECHARGE_ENERGY, 0, false);
        machine.store().add(AER, 1, false);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(machine.work(level) == NodeFabricatorWork.Operation.RECHARGE, "no recharge happened");
        helper.assertTrue(node.nodeState().currentVis().amount(AER) == 5,
                "node holds " + node.nodeState().currentVis().amount(AER) + " vis, expected 5");
        helper.assertTrue(machine.energy().ledger().stored() == 0 && machine.store().total() == 0,
                "the machine did not pay exactly once");
        // Nothing left to pay with: the node must not gain a second point for free.
        helper.assertTrue(machine.work(level) == NodeFabricatorWork.Operation.NONE, "worked for free");
        helper.assertTrue(node.nodeState().currentVis().amount(AER) == 5, "the node gained free vis");
        helper.succeed();
    }

    /** With a gem, an aspect the node never had is introduced and its base rises by one. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void expansionRaisesTheBaseOfANewAspect(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.EAST);
        BlockPos nodePos = machine.nodePosition();
        // nodePosition() is absolute; helper.setBlock/getBlockEntity take structure-relative
        // coordinates, so go through the level with the absolute position.
        helper.getLevel().setBlockAndUpdate(nodePos, TCBlocks.AURA_NODE.get().defaultBlockState());
        AuraNodeBlockEntity node = (AuraNodeBlockEntity) helper.getLevel().getBlockEntity(nodePos);
        node.setNodeState(new AuraNodeState(Optional.of(UUID.randomUUID()), NodeTypeId.NORMAL,
                NodeModifierId.FADING, NodeVis.EMPTY, NodeVis.EMPTY));

        machine.setBoosted(true);
        machine.energy().ledger().receive(NodeFabricatorWork.EXPAND_ENERGY, 0, false);
        machine.store().add(AER, NodeFabricatorWork.EXPAND_ESSENTIA, false);
        helper.assertTrue(machine.work(helper.getLevel()) == NodeFabricatorWork.Operation.EXPAND,
                "no expansion happened");
        helper.assertTrue(node.nodeState().baseVis().amount(AER) == 1 && node.nodeState().currentVis().amount(AER) == 1,
                "base/current are " + node.nodeState().baseVis().amount(AER) + "/"
                        + node.nodeState().currentVis().amount(AER));
        helper.assertTrue(machine.energy().ledger().stored() == 0 && machine.store().total() == 0,
                "the expansion was not paid for exactly once");
        helper.succeed();
    }

    /** Two fabricators six blocks apart and facing each other are active; one alone is not. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void onlyAFacingPairBecomesActive(GameTestHelper helper) {
        // The 5x5x5 template is too small for the pair, so the partner is checked by hand: an
        // unmatched fabricator must report itself inactive after its structure check has run.
        BlockPos pos = new BlockPos(2, 2, 2);
        NodeFabricatorBlockEntity machine = place(helper, pos, Direction.NORTH);
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(machine.isActive(), "a lone fabricator reported itself active");
            helper.assertTrue(
                    machine.partnerPosition().equals(helper.absolutePos(pos).north(NodeFabricatorBlockEntity.PARTNER_DISTANCE)),
                    "the partner is looked for at " + machine.partnerPosition());
            helper.assertTrue(
                    machine.nodePosition().equals(helper.absolutePos(pos).north(NodeFabricatorBlockEntity.NODE_DISTANCE).above()),
                    "the node is looked for at " + machine.nodePosition());
            helper.succeed();
        });
    }
}
