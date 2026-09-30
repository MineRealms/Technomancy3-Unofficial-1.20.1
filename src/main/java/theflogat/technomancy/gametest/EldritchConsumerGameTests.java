package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.machines.consumer.ConsumerRange;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.machines.EldritchConsumerBlockEntity;

/**
 * In-world behaviour of the eldritch consumer: it spends energy to eat, it stops when it cannot
 * pay, it spares unbreakable blocks and block-entity blocks, a broken block yields aspects but
 * not drops, the aspect it gathers leaves through every face, and a pass raises the working flag
 * the client's panel animation is driven by.
 *
 * <p>Ranges and positions are absolute. The machine sits one block above its food so the scans
 * only ever see what each test placed.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EldritchConsumerGameTests {

    private static final String BATCH = "technom_s2_consumer";
    private static final BlockPos MACHINE = new BlockPos(2, 3, 2);
    private static final AspectId IGNIS = AspectId.parse("ignis");
    private static final long COST = 20_000;

    private EldritchConsumerGameTests() {}

    /** With no energy, nothing is eaten and no aspect is gained. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void nothingIsEatenWithoutEnergy(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        ServerLevel level = helper.getLevel();
        spawnItem(helper, new ItemStack(Items.IRON_INGOT, 2));
        run(helper, consumer, 40);
        helper.assertTrue(countItems(helper) == 2, "a drop was eaten by an unpowered consumer");
        helper.assertTrue(consumer.store().total() == 0,
                "an unpowered consumer gained " + consumer.store().total() + " aspect");
        helper.succeed();
    }

    /** A powered consumer eats one drop per charge and stores what it was made of. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 400)
    public static void dropsAreEatenForTheirAspectAndOneCharge(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        fill(consumer, COST * 8);
        spawnItem(helper, new ItemStack(Items.IRON_INGOT, 2));
        run(helper, consumer, 200);
        helper.assertTrue(countItems(helper) == 0, "the consumer left food behind");
        helper.assertTrue(consumer.store().total() > 0,
                "the consumer ate an iron ingot but stored nothing");
        long spent = COST * 8 - consumer.energy().ledger().stored();
        helper.assertTrue(spent > 0 && spent % COST == 0,
                "spent " + spent + " Q, which is not a whole number of charges");
        Technomancy.LOGGER.info("GameTest consumer: two ingots cost {} Q and stored {}",
                spent, consumer.store().visibleAspects().amounts());
        helper.succeed();
    }

    /**
     * A pass raises the working flag and the flag survives exactly the 40-tick cooldown. The
     * client reads nothing else: {@code working} is what the renderer passes to the model's
     * {@code go} argument, so if it cleared early the panel would rise while the machine was still
     * in its tail.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 400)
    public static void aPassRaisesTheWorkingFlagForTheWholeCooldown(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        fill(consumer, COST * 8);
        helper.assertFalse(consumer.working(), "the consumer was already working before it ate");
        spawnItem(helper, new ItemStack(Items.IRON_INGOT, 1));

        // One tick is a whole pass: the ingot is eaten and the cooldown starts.
        run(helper, consumer, 1);
        helper.assertTrue(consumer.working(), "a successful pass did not raise the working flag");
        helper.assertTrue(consumer.panelRotation() == 0.0F,
                "the server-side panel moved, which only the client may do");

        // The flag must outlive the pass itself by the whole cooldown, and no longer.
        run(helper, consumer, EldritchConsumerBlockEntity.COOLDOWN_TICKS - 1);
        helper.assertTrue(consumer.working(), "the flag cleared before the cooldown ran out");
        run(helper, consumer, 1);
        helper.assertFalse(consumer.working(), "the flag outlived the cooldown");
        helper.succeed();
    }

    /** A block yields its aspect, and is removed without spawning the drops it would have. */    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void aBlockYieldsItsAspectButNotItsDrops(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        fill(consumer, COST * 8);
        ServerLevel level = helper.getLevel();
        BlockPos dirt = helper.absolutePos(MACHINE.below());
        level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());

        run(helper, consumer, 100);
        helper.assertTrue(level.getBlockState(dirt).isAir(), "the block below survived a powered consumer");
        helper.assertTrue(consumer.store().total() > 0, "the broken block yielded no aspect");
        helper.assertTrue(countItems(helper) == 0, "the broken block was turned into a drop as well");
        helper.succeed();
    }

    /** Bedrock and a chest are left exactly where they are. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void unbreakableAndBlockEntityBlocksAreSpared(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        fill(consumer, COST * 8);
        ServerLevel level = helper.getLevel();
        BlockPos bedrock = helper.absolutePos(MACHINE.below());
        BlockPos chest = helper.absolutePos(MACHINE.below().east());
        level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());

        run(helper, consumer, 100);
        helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock was eaten");
        helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST),
                "a block entity's block was eaten");
        helper.assertTrue(consumer.store().total() == 0,
                "the consumer gained aspect from protected blocks");
        helper.succeed();
    }

    /** The store empties through every face, and the machine never asks for essentia. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void essentiaLeavesThroughEveryFace(GameTestHelper helper) {
        EldritchConsumerBlockEntity consumer = place(helper, ConsumerRange.SMALL);
        consumer.store().add(IGNIS, 4, false);
        for (Direction face : Direction.values()) {
            helper.assertTrue(consumer.canOutputTo(face), face + ": essentia cannot leave");
            helper.assertFalse(consumer.canInputFrom(face), face + ": essentia can go in");
            helper.assertTrue(consumer.isConnectable(face), face + ": the face is not connectable");
        }
        helper.assertTrue(consumer.availableEssentia(IGNIS, Direction.NORTH) == 4,
                "the stored ignis is not advertised");
        helper.assertTrue(consumer.takeEssentia(IGNIS, 4, Direction.NORTH, EssentiaTransferMode.EXECUTE) == 4,
                "the machine did not give up the ignis it held");
        for (Direction face : Direction.values()) {
            helper.assertTrue(consumer.availableEssentia(IGNIS, face) == 0,
                    face + ": advertises ignis after the store was emptied");
            helper.assertTrue(consumer.takeEssentia(IGNIS, 1, face, EssentiaTransferMode.EXECUTE) == 0,
                    face + ": still hands out essentia from an empty store");
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static EldritchConsumerBlockEntity place(GameTestHelper helper, ConsumerRange range) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        BlockPos pos = helper.absolutePos(MACHINE);
        level.setBlockAndUpdate(pos, TechnomBlocks.ELDRITCH_CONSUMER.get().defaultBlockState());
        // The original defaults to running only on a signal; a redstone block supplies one.
        level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof EldritchConsumerBlockEntity consumer)) {
            throw new IllegalStateException("no consumer at " + pos);
        }
        consumer.setRange(range);
        return consumer;
    }

    /**
     * Restores the air space above the template floor and clears entities.
     *
     * <p>The tests in one batch share a world: a structure re-placement resets the blocks but not
     * the entities, and a consumer eats both. Without this the next test would find the previous
     * test's food and count it as its own.</p>
     */
    private static void prepare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, box(helper)).forEach(ItemEntity::discard);
    }

    private static void fill(EldritchConsumerBlockEntity consumer, long q) {
        long added = consumer.energy().ledger().generate(q);
        if (added != q) {
            throw new IllegalStateException("could only buffer " + added + " of " + q + " Q");
        }
    }

    private static void run(GameTestHelper helper, EldritchConsumerBlockEntity consumer, int ticks) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = level.getBlockState(pos);
        for (int tick = 0; tick < ticks; tick++) {
            EldritchConsumerBlockEntity.serverTick(level, pos, state, consumer);
        }
    }

    private static void spawnItem(GameTestHelper helper, ItemStack stack) {
        BlockPos pos = helper.absolutePos(MACHINE.below());
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
    }

    private static int countItems(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box(helper)).stream()
                .mapToInt(item -> item.getItem().getCount())
                .sum();
    }

    private static AABB box(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(MACHINE);
        return new AABB(pos.getX() - 2, pos.getY() - 4, pos.getZ() - 2,
                pos.getX() + 3, pos.getY() + 1, pos.getZ() + 3);
    }
}
