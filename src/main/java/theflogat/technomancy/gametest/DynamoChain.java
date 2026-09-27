package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import theflogat.technomancy.common.blocks.dynamo.EssentiaDynamoBlock;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.FuelEnvironment;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;

/**
 * A real essentia chain in a test world: a Thaumcraft warded jar, a dynamo above it, and an
 * optional Forge Energy receiver above that.
 *
 * <p>Stacked vertically on purpose. A GameTest structure may be placed at any horizontal
 * rotation, while the dynamo's facing and its port masks are absolute world directions; up and
 * down are the two directions a rotation cannot touch.</p>
 */
final class DynamoChain {

    static final AspectId IGNIS = AspectId.parse("ignis");
    /** {@code WardedJarBlockEntity.CAPACITY}. */
    static final int JAR_CAPACITY = 64;

    private final GameTestHelper helper;
    private final BlockPos jar;
    private final BlockPos dynamo;
    @Nullable
    private final GameTestEnergySink sink;

    private DynamoChain(GameTestHelper helper, BlockPos jar, BlockPos dynamo,
            @Nullable GameTestEnergySink sink) {
        this.helper = helper;
        this.jar = jar;
        this.dynamo = dynamo;
        this.sink = sink;
    }

    /**
     * Builds the chain at the centre of the test room and fills the jar with Ignis through the
     * public essentia API.
     *
     * @param withSink whether to place a Forge Energy receiver on the dynamo's output face
     */
    static DynamoChain place(GameTestHelper helper, boolean withSink) {
        ServerLevel level = helper.getLevel();
        BlockPos jarPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dynamoPos = jarPos.above();
        BlockPos sinkPos = dynamoPos.above();

        Block wardedJar = BuiltInRegistries.BLOCK.get(new ResourceLocation("thaumcraft", "warded_jar"));
        helper.assertTrue(wardedJar != Blocks.AIR, "Thaumcraft has no warded_jar block to feed the dynamo");
        level.setBlockAndUpdate(jarPos, wardedJar.defaultBlockState());
        level.setBlockAndUpdate(dynamoPos, TechnomBlocks.ESSENTIA_DYNAMO.get().defaultBlockState()
                .setValue(EssentiaDynamoBlock.FACING, Direction.UP));

        GameTestEnergySink sink = withSink
                // Room for far more than a test can generate, and a per-call ceiling above the
                // dynamo's own 320 Q/t, so the dynamo stays the bottleneck.
                ? GameTestEnergySink.placeAt(level, sinkPos, 1_000_000, 4096)
                : null;

        DynamoChain chain = new DynamoChain(helper, jarPos, dynamoPos, sink);
        helper.assertTrue(chain.jarTransport() != null, "the warded jar has no essentia transport");
        helper.assertTrue(chain.dynamo() != null, "the dynamo block entity was not created");

        int filled = EssentiaApi.add(level, chain.jarTransport(), IGNIS, JAR_CAPACITY,
                Direction.UP, EssentiaTransferMode.EXECUTE);
        helper.assertTrue(filled == JAR_CAPACITY,
                "the warded jar accepted " + filled + " of " + JAR_CAPACITY + " ignis");
        return chain;
    }

    EssentiaDynamoBlockEntity dynamo() {
        return helper.getLevel().getBlockEntity(dynamo) instanceof EssentiaDynamoBlockEntity be ? be : null;
    }

    EssentiaTransport jarTransport() {
        return helper.getLevel().getBlockEntity(jar) instanceof EssentiaTransport transport
                ? transport : null;
    }

    BlockPos dynamoPos() {
        return dynamo;
    }

    /** Essentia the jar still holds, seen through the transport contract. */
    int jarAmount() {
        return jarTransport().essentiaAmount(Direction.UP);
    }

    int dynamoAmount() {
        return dynamo().store().total();
    }

    long dynamoEnergy() {
        return dynamo().energy().ledger().stored();
    }

    long delivered() {
        return sink == null ? 0 : sink.everReceived();
    }

    /** Q one unit of Ignis is worth here, taken from the live data-driven table. */
    long energyPerUnit() {
        return EssentiaFuelLoader.table()
                .energyPerUnit(FuelEnvironment.of(helper.getLevel(), dynamo), IGNIS);
    }

    /** Powers the dynamo by dropping a redstone block against one of its essentia faces. */
    void power() {
        helper.getLevel().setBlockAndUpdate(dynamo.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }
}
