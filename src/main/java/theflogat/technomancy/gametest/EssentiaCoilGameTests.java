package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.coils.CoilBlock;
import theflogat.technomancy.common.coils.CoilLink;
import theflogat.technomancy.common.coils.CoilLinks;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.coils.EssentiaCoilBlockEntity;

/**
 * The essentia coil against real Thaumcraft warded jars: it pushes into the jar it stands on,
 * serves a consumer that pulls from it, respects its filter, and conserves essentia either way.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EssentiaCoilGameTests {

    private static final String BATCH = CoilGameTests.BATCH;
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId ORDO = AspectId.parse("ordo");
    private static final int FILL = 32;

    private EssentiaCoilGameTests() {
    }

    /** Source jar at (0,1,0), target jar at (2,1,2), coil on top of the target facing down. */
    record EssentiaRig(GameTestHelper helper, BlockPos source, BlockPos target, BlockPos coil) {

        static EssentiaRig place(GameTestHelper helper, AspectId filled) {
            ServerLevel level = helper.getLevel();
            Block jar = BuiltInRegistries.BLOCK.get(new ResourceLocation("thaumcraft", "warded_jar"));
            helper.assertTrue(jar != Blocks.AIR, "Thaumcraft has no warded_jar block");
            BlockPos source = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
            BlockPos coil = target.above();
            level.setBlockAndUpdate(source, jar.defaultBlockState());
            level.setBlockAndUpdate(target, jar.defaultBlockState());
            level.setBlockAndUpdate(coil, TechnomBlocks.ESSENTIA_COIL.get().defaultBlockState()
                    .setValue(CoilBlock.FACING, Direction.DOWN));

            EssentiaRig rig = new EssentiaRig(helper, source, target, coil);
            helper.assertTrue(rig.coilEntity() != null, "the essentia coil block entity was not created");
            helper.assertTrue(rig.transport(source) instanceof EssentiaSource,
                    "the warded jar is not a drainable essentia source");
            int added = EssentiaApi.add(level, rig.transport(source), filled, FILL, Direction.UP,
                    EssentiaTransferMode.EXECUTE);
            helper.assertTrue(added == FILL, "the source jar accepted " + added + " of " + FILL);
            return rig;
        }

        EssentiaTransport transport(BlockPos pos) {
            return helper.getLevel().getBlockEntity(pos) instanceof EssentiaTransport transport ? transport : null;
        }

        EssentiaCoilBlockEntity coilEntity() {
            return helper.getLevel().getBlockEntity(coil) instanceof EssentiaCoilBlockEntity be ? be : null;
        }

        int amount(BlockPos jar, AspectId aspect) {
            EssentiaTransport transport = transport(jar);
            return transport == null ? 0 : transport.availableEssentia(aspect, Direction.UP);
        }

        void link() {
            helper.assertTrue(coilEntity().links().add(coil, new CoilLink(source, Direction.UP))
                    == CoilLinks.Result.ADDED, "the source jar could not be linked");
        }
    }

    /** Push: the coil moves essentia from a linked jar into the jar it stands on, losing none. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void theCoilFillsTheJarItStandsOnAndConservesEssentia(GameTestHelper helper) {
        EssentiaRig rig = EssentiaRig.place(helper, IGNIS);
        rig.link();
        helper.runAfterDelay(40, () -> {
            int left = rig.amount(rig.source(), IGNIS);
            int moved = rig.amount(rig.target(), IGNIS);
            helper.assertTrue(moved > 0, "the coil moved nothing in 40 ticks");
            helper.assertTrue(left + moved == FILL,
                    "essentia was not conserved: " + left + " left in the source, " + moved + " arrived, of " + FILL);
            Technomancy.LOGGER.info("GameTest essentia coil: {} of {} units of ignis moved wirelessly in 40 ticks",
                    moved, FILL);
            helper.succeed();
        });
    }

    /** Pull: a consumer takes through the coil, which fetches from the link. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void aConsumerCanPullThroughTheCoil(GameTestHelper helper) {
        EssentiaRig rig = EssentiaRig.place(helper, IGNIS);
        rig.link();
        EssentiaCoilBlockEntity coil = rig.coilEntity();
        // The face the coil stands on is the one it serves; EssentiaApi.take enforces the
        // contract a real Thaumatorium or Arcane Bore goes through.
        helper.assertTrue(coil.canOutputTo(Direction.DOWN) && !coil.canInputFrom(Direction.DOWN),
                "the coil must be an output-only endpoint on the face it stands on");
        helper.assertTrue(IGNIS.equals(coil.essentiaType(Direction.DOWN)),
                "the coil does not offer what its link holds");
        helper.assertTrue(coil.availableEssentia(IGNIS, Direction.DOWN) > 0, "the coil reports nothing available");

        int simulated = EssentiaApi.take(helper.getLevel(), coil, IGNIS, 1, Direction.DOWN,
                EssentiaTransferMode.SIMULATE);
        helper.assertTrue(simulated == 1, "simulation returned " + simulated);
        helper.assertTrue(rig.amount(rig.source(), IGNIS) == FILL, "simulation drained the source");

        int taken = EssentiaApi.take(helper.getLevel(), coil, IGNIS, 1, Direction.DOWN, EssentiaTransferMode.EXECUTE);
        helper.assertTrue(taken == 1, "the consumer got " + taken);
        helper.assertTrue(rig.amount(rig.source(), IGNIS) == FILL - 1,
                "the source lost " + (FILL - rig.amount(rig.source(), IGNIS)) + " for one unit delivered");

        // Deliberate: a coil is not a remote source, so an infusion altar cannot reach through it
        // into its links. Checked against the class, since javac already rejects the instanceof.
        helper.assertTrue(!EssentiaSource.class.isAssignableFrom(EssentiaCoilBlockEntity.class),
                "the coil must not be an EssentiaSource");
        helper.succeed();
    }

    /** A filter that does not match the link's contents moves nothing at all. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 120)
    public static void theFilterBlocksOtherAspects(GameTestHelper helper) {
        EssentiaRig rig = EssentiaRig.place(helper, IGNIS);
        rig.coilEntity().setFilter(ORDO);
        rig.link();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(rig.amount(rig.source(), IGNIS) == FILL, "a filtered coil moved the wrong aspect");
            helper.assertTrue(rig.amount(rig.target(), IGNIS) == 0, "the target received a filtered-out aspect");
            helper.assertTrue(rig.coilEntity().essentiaType(Direction.DOWN) == null,
                    "a coil whose filter nothing supplies must offer nothing");
            helper.succeed();
        });
    }

    /** A powered coil neither pushes nor serves, and resumes when the signal goes. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void redstoneGatesBothDirections(GameTestHelper helper) {
        EssentiaRig rig = EssentiaRig.place(helper, IGNIS);
        rig.link();
        helper.getLevel().setBlockAndUpdate(rig.coil().east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(rig.amount(rig.source(), IGNIS) == FILL, "a powered coil pushed essentia");
            helper.assertTrue(rig.coilEntity().availableEssentia(IGNIS, Direction.DOWN) == 0,
                    "a powered coil still served a consumer");
            helper.getLevel().setBlockAndUpdate(rig.coil().east(), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(rig.amount(rig.target(), IGNIS) > 0, "the coil did not resume when unpowered");
                helper.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theFilterAndLinksSurviveSaveAndLoad(GameTestHelper helper) {
        EssentiaRig rig = EssentiaRig.place(helper, IGNIS);
        rig.link();
        rig.coilEntity().setFilter(IGNIS);
        CompoundTag saved = rig.coilEntity().saveWithoutMetadata();
        EssentiaCoilBlockEntity restored =
                new EssentiaCoilBlockEntity(rig.coil(), helper.getLevel().getBlockState(rig.coil()));
        restored.load(saved);
        helper.assertTrue(IGNIS.equals(restored.filter()), "the filter was lost on reload");
        helper.assertTrue(restored.links().view().equals(rig.coilEntity().links().view()),
                "the links changed on reload");
        helper.succeed();
    }
}
