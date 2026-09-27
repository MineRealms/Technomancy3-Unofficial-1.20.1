package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.machines.EssentiaFusorBlock;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.machines.fusor.EssentiaFusorBalance;
import theflogat.technomancy.common.machines.fusor.FusorSides;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.machines.EssentiaFusorBlockEntity;

/**
 * S2 essentia fusor in a real world, against the live aspect registry. Written for the final
 * batch run.
 *
 * <p>The component pair is discovered from the registry rather than hard-coded, so these tests
 * assert the machine and not a guess about TC4R's aspect data.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S2FusorGameTests {

    private static final String BATCH = "technom_s2_fusor";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    private S2FusorGameTests() {}

    /** A real combination from the registry can be configured, and it fuses two units into one. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void aFusionConsumesTwoUnitsForOneAndPaysTheBalancedCost(GameTestHelper helper) {
        EssentiaFusorBlockEntity fusor = place(helper);
        AspectId[] pair = anyCombination(helper);
        FusorSides sides = fusor.sides();
        helper.assertTrue(sides.markInput(Direction.NORTH, pair[0], EssentiaFusorBlockEntity.combiner()),
                "could not mark the first input");
        helper.assertTrue(sides.markInput(Direction.SOUTH, pair[1], EssentiaFusorBlockEntity.combiner()),
                "could not mark the second input with a component that combines");
        helper.assertTrue(sides.markOutput(Direction.WEST, EssentiaFusorBlockEntity.combiner()),
                "could not mark the output");
        helper.assertTrue(pair[2].equals(sides.outputAspect()),
                "the recipe resolved to " + sides.outputAspect() + " instead of " + pair[2]);
        // The default is HIGH, i.e. it only runs when powered; there is no signal here.
        helper.assertTrue(fusor.redstone().mode() == EssentiaFusorBlockEntity.DEFAULT_REDSTONE,
                "the default redstone mode changed");

        helper.assertTrue(fusor.addEssentia(pair[0], 10, Direction.NORTH, EssentiaTransferMode.EXECUTE) == 10,
                "the first input refused essentia");
        helper.assertTrue(fusor.addEssentia(pair[1], 10, Direction.SOUTH, EssentiaTransferMode.EXECUTE) == 10,
                "the second input refused essentia");
        helper.assertTrue(fusor.addEssentia(pair[0], 1, Direction.EAST, EssentiaTransferMode.EXECUTE) == 0,
                "an unmarked side accepted essentia");
        long cost = fusor.fusionCostQ();
        helper.assertTrue(cost >= EssentiaFusorBalance.LEGACY_COST_Q, "the cost fell below the floor");
        fusor.energy().ledger().generate(cost * 4);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(sides.amount(Direction.WEST) == 0,
                    "an unpowered fusor fused anyway (redstone HIGH)");
            helper.assertTrue(fusor.energy().ledger().stored() == cost * 4,
                    "an unpowered fusor spent energy");
            fusor.redstone().set(RedstoneMode.NONE);
        });
        helper.runAfterDelay(30, () -> {
            int made = sides.amount(Direction.WEST);
            helper.assertTrue(made > 0, "a powered, fed and charged fusor made nothing");
            helper.assertTrue(made <= 4, "it made " + made + " units but could only afford 4");
            helper.assertTrue(sides.amount(Direction.NORTH) == 10 - made
                            && sides.amount(Direction.SOUTH) == 10 - made,
                    "each fusion must consume exactly one unit from each input");
            helper.assertTrue(sides.totalStored() == 20 - made,
                    "the total is " + sides.totalStored() + " instead of " + (20 - made));
            helper.assertTrue(fusor.energy().ledger().stored() == cost * 4 - cost * made,
                    "energy spent does not match " + made + " fusions at " + cost + " Q");
            Technomancy.LOGGER.info("GameTest fusor: {} + {} -> {} x{} at {} Q each",
                    pair[0], pair[1], pair[2], made, cost);
            helper.succeed();
        });
    }

    /** A full output stops the machine without spending anything, and the faces behave. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void aFullOutputAndTheFaceRulesCostNothing(GameTestHelper helper) {
        EssentiaFusorBlockEntity fusor = place(helper);
        AspectId[] pair = anyCombination(helper);
        FusorSides sides = fusor.sides();
        sides.markInput(Direction.NORTH, pair[0], EssentiaFusorBlockEntity.combiner());
        sides.markInput(Direction.SOUTH, pair[1], EssentiaFusorBlockEntity.combiner());
        sides.markOutput(Direction.WEST, EssentiaFusorBlockEntity.combiner());
        fusor.redstone().set(RedstoneMode.NONE);
        sides.add(Direction.NORTH, pair[0], 5, false);
        sides.add(Direction.SOUTH, pair[1], 5, false);
        sides.add(Direction.WEST, pair[2], FusorSides.MAX_AMOUNT, false);
        long charge = fusor.fusionCostQ() * 4;
        fusor.energy().ledger().generate(charge);

        for (Direction face : Direction.values()) {
            boolean input = fusor.canInputFrom(face);
            boolean output = fusor.canOutputTo(face);
            helper.assertTrue(fusor.isConnectable(face) == (input || output),
                    face + ": isConnectable disagrees with the transfer rules (defect A-7)");
            helper.assertTrue(input == (face == Direction.NORTH || face == Direction.SOUTH),
                    face + ": input is " + input);
            helper.assertTrue(output == (face == Direction.WEST), face + ": output is " + output);
            if (!output) {
                helper.assertTrue(fusor.essentiaAmount(face) == 0 && fusor.essentiaType(face) == null,
                        face + ": advertises essentia a tube could try to pull");
                helper.assertTrue(fusor.takeEssentia(pair[0], 1, face, EssentiaTransferMode.EXECUTE) == 0,
                        face + ": handed essentia out through a face that gives nothing");
            }
        }
        helper.assertTrue(fusor.suctionAmount(Direction.WEST) == EssentiaFusorBlockEntity.OUTPUT_SUCTION
                        && fusor.minimumSuction() == EssentiaFusorBlockEntity.OUTPUT_SUCTION,
                "the output does not advertise negative suction");
        helper.assertTrue(fusor.suctionAmount(Direction.NORTH) == EssentiaFusorBlockEntity.INPUT_SUCTION,
                "a hungry input does not advertise suction");

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(sides.amount(Direction.WEST) == FusorSides.MAX_AMOUNT
                            && sides.amount(Direction.NORTH) == 5,
                    "a full output did not stop the machine");
            helper.assertTrue(fusor.energy().ledger().stored() == charge,
                    "a blocked fusor spent energy");
            // Draining the output lets it run again.
            helper.assertTrue(fusor.takeEssentia(pair[2], 10, Direction.WEST,
                    EssentiaTransferMode.EXECUTE) == 10, "the output could not be drained");
            helper.succeed();
        });
    }

    /** The four quadrants of the top face map to the four sides, as the original's math did. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theTopFaceQuadrantsSelectTheFourSides(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        for (Direction slot : FusorSides.SLOTS) {
            double x = pos.getX() + 0.5 + 0.3 * slot.getStepX();
            double z = pos.getZ() + 0.5 + 0.3 * slot.getStepZ();
            Direction hit = EssentiaFusorBlock.slotAt(new Vec3(x, pos.getY() + 0.75, z), pos);
            helper.assertTrue(slot == hit, "the " + slot + " quadrant resolved to " + hit);
        }
        helper.assertTrue(EssentiaFusorBlock.slotAt(
                        new Vec3(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5), pos) == null,
                "the middle of the top face is not a slot");
        helper.assertTrue(EssentiaFusorBlock.slotAt(
                        new Vec3(pos.getX() + 0.1, pos.getY() + 0.75, pos.getZ() + 0.1), pos) == null,
                "a corner is not a slot");
        helper.succeed();
    }

    private static EssentiaFusorBlockEntity place(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.getLevel().setBlockAndUpdate(pos, TechnomBlocks.ESSENTIA_FUSOR.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof EssentiaFusorBlockEntity fusor)) {
            throw new IllegalStateException("no fusor block entity at " + pos);
        }
        return fusor;
    }

    /**
     * Any {@code {first, second, combined}} the live registry actually defines, so the test does
     * not assume a particular aspect data pack.
     */
    private static AspectId[] anyCombination(GameTestHelper helper) {
        for (AspectId first : AspectApi.primals()) {
            for (AspectId second : AspectApi.primals()) {
                if (first.equals(second)) {
                    continue;
                }
                Optional<AspectId> combined = AspectApi.combination(first, second);
                if (combined.isPresent()) {
                    return new AspectId[] {first, second, combined.get()};
                }
            }
        }
        helper.fail("the aspect registry defines no combination of two primals at all");
        throw new IllegalStateException("unreachable");
    }
}
