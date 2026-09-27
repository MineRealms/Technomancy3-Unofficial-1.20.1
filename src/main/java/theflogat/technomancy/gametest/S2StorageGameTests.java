package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSearch;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSourceRef;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;
import theflogat.technomancy.common.tiles.essentia.EssentiaReservoirBlockEntity;

/** S2 storage: the essentia reservoir and the creative jar. Written for the final batch run. */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S2StorageGameTests {

    private static final String BATCH = "technom_s2_storage";
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final AspectId AQUA = AspectId.parse("aqua");

    private S2StorageGameTests() {}

    /** Every transfer reports what actually moved; the original returned takes inverted. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void reservoirReportsExactlyWhatMoves(GameTestHelper helper) {
        EssentiaReservoirBlockEntity reservoir = placeReservoir(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        check(helper, reservoir.addEssentia(IGNIS, 10, Direction.NORTH, EssentiaTransferMode.EXECUTE) == 10, "add 10");
        check(helper, reservoir.takeEssentia(IGNIS, 4, Direction.EAST, EssentiaTransferMode.SIMULATE) == 4
                && reservoir.amount() == 10, "a simulated take changed the contents");
        check(helper, reservoir.takeEssentia(IGNIS, 4, Direction.EAST, EssentiaTransferMode.EXECUTE) == 4
                && reservoir.amount() == 6, "take 4");
        check(helper, reservoir.takeEssentia(IGNIS, 100, Direction.UP, EssentiaTransferMode.EXECUTE) == 6
                && reservoir.amount() == 0, "a take larger than the contents yields the contents");
        check(helper, reservoir.takeEssentia(IGNIS, 1, Direction.UP, EssentiaTransferMode.EXECUTE) == 0, "empty");
        check(helper, EssentiaApi.add(level, reservoir, AQUA, 300, Direction.DOWN, EssentiaTransferMode.EXECUTE)
                == EssentiaReservoirBlockEntity.CAPACITY, "fill to capacity through the validated API");
        check(helper, reservoir.addEssentia(AQUA, 1, Direction.DOWN, EssentiaTransferMode.EXECUTE) == 0, "full");
        check(helper, reservoir.suctionAmount(Direction.DOWN) == 0, "a full reservoir still advertises suction");
        check(helper, EssentiaApi.take(level, reservoir, AQUA, 1000, Direction.WEST, EssentiaTransferMode.EXECUTE)
                == EssentiaReservoirBlockEntity.CAPACITY && reservoir.amount() == 0, "drain through the validated API");
        check(helper, reservoir.addEssentia(IGNIS, 3, Direction.UP, EssentiaTransferMode.EXECUTE) == 3
                && reservoir.addEssentia(AQUA, 3, Direction.UP, EssentiaTransferMode.EXECUTE) == 0,
                "one aspect at a time");
        check(helper, reservoir.suctionAmount(Direction.UP) == EssentiaReservoirBlockEntity.SUCTION
                && reservoir.minimumSuction() == 0, "suction 128 / minimum 0");
        helper.succeed();
    }

    /**
     * Two columns, each a warded jar under a reservoir that already holds 5 ignis. The ignis jar
     * is drained into its reservoir; the aqua jar must keep every unit, because the original took
     * the unit first and then threw away whatever the store refused.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void reservoirDrainsMatchingJarsAndNeverDeletesAMismatch(GameTestHelper helper) {
        BlockPos jarA = new BlockPos(1, 1, 1);
        BlockPos jarB = new BlockPos(3, 1, 3);
        EssentiaTransport ignisJar = placeWardedJar(helper, jarA, IGNIS, 10);
        EssentiaTransport aquaJar = placeWardedJar(helper, jarB, AQUA, 10);
        EssentiaReservoirBlockEntity a = placeReservoir(helper, jarA.above());
        EssentiaReservoirBlockEntity b = placeReservoir(helper, jarB.above());
        a.addEssentia(IGNIS, 5, Direction.UP, EssentiaTransferMode.EXECUTE);
        b.addEssentia(IGNIS, 5, Direction.UP, EssentiaTransferMode.EXECUTE);
        helper.runAfterDelay(60, () -> {
            int jarLeft = ignisJar.availableEssentia(IGNIS, Direction.UP);
            check(helper, a.amount() + jarLeft == 15, "ignis not conserved: reservoir " + a.amount() + " + jar " + jarLeft);
            check(helper, jarLeft == 0, "the reservoir did not drain the matching jar (" + jarLeft + " left)");
            check(helper, aquaJar.availableEssentia(AQUA, Direction.UP) == 10,
                    "the mismatched jar lost aqua: " + aquaJar.availableEssentia(AQUA, Direction.UP));
            check(helper, b.amount() == 5 && IGNIS.equals(b.aspect()), "the ignis reservoir changed");
            Technomancy.LOGGER.info("GameTest reservoir: drained {} ignis from a jar, left 10 aqua alone", 10 - jarLeft);
            helper.succeed();
        });
    }

    /** An infinite source still satisfies TC4R's validated take/extract paths without throwing. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void creativeJarIsAnInfiniteContractAbidingSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos jarPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(jarPos, TechnomBlocks.CREATIVE_JAR.get().defaultBlockState());
        CreativeJarBlockEntity jar = (CreativeJarBlockEntity) level.getBlockEntity(jarPos);
        check(helper, jar != null, "no creative jar block entity");
        check(helper, jar.extractEssentia(IGNIS, 8, EssentiaTransferMode.EXECUTE) == 0, "an unset jar gave essentia");
        check(helper, jar.setAspect(IGNIS), "could not set the aspect");

        check(helper, EssentiaApi.take(level, jar, IGNIS, 1000, Direction.UP, EssentiaTransferMode.EXECUTE) == 1000,
                "validated take of 1000");
        check(helper, EssentiaApi.take(level, jar, IGNIS, 5, Direction.NORTH, EssentiaTransferMode.EXECUTE) == 0,
                "a side face gave essentia");
        Optional<EssentiaSourceRef> found = EssentiaApi.findSource(level, jarPos.above(), IGNIS, 64,
                EssentiaSearch.nearby(2));
        check(helper, found.isPresent(), "a range search did not find the creative jar");
        check(helper, EssentiaApi.extract(level, found.get(), IGNIS, 64, EssentiaTransferMode.EXECUTE) == 64,
                "all-or-nothing extraction of 64");
        check(helper, level.getBlockState(jarPos).getDestroySpeed(level, jarPos) < 0,
                "breakable outside creative, so a survival player could harvest it");
        check(helper, TechnomBlocks.CREATIVE_JAR.get().getLootTable().equals(BuiltInLootTables.EMPTY),
                "the creative jar has a loot table");

        // A reservoir on top drains it one unit per tick.
        EssentiaReservoirBlockEntity reservoir = placeReservoir(helper, new BlockPos(2, 2, 2));
        helper.runAfterDelay(40, () -> {
            check(helper, reservoir.amount() > 0 && IGNIS.equals(reservoir.aspect()),
                    "the reservoir pulled nothing from the creative jar");
            Technomancy.LOGGER.info("GameTest creative jar: reservoir pulled {} ignis in 40 ticks", reservoir.amount());
            helper.succeed();
        });
    }

    static EssentiaReservoirBlockEntity placeReservoir(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, TechnomBlocks.ESSENTIA_RESERVOIR.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof EssentiaReservoirBlockEntity reservoir)) {
            throw new IllegalStateException("no reservoir block entity at " + pos);
        }
        return reservoir;
    }

    static EssentiaTransport placeWardedJar(GameTestHelper helper, BlockPos relative, AspectId aspect, int amount) {
        Block jar = BuiltInRegistries.BLOCK.get(new ResourceLocation("thaumcraft", "warded_jar"));
        check(helper, jar != Blocks.AIR, "Thaumcraft has no warded_jar");
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, jar.defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof EssentiaTransport transport)) {
            throw new IllegalStateException("the warded jar has no essentia transport");
        }
        int filled = EssentiaApi.add(helper.getLevel(), transport, aspect, amount, Direction.UP,
                EssentiaTransferMode.EXECUTE);
        check(helper, filled == amount, "the warded jar accepted " + filled + " of " + amount);
        return transport;
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
