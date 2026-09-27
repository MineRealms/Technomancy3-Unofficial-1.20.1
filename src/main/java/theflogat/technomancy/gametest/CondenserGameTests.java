package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.block.entity.WardedJarBlockEntity;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.machines.EnergyCondenserBlock;
import theflogat.technomancy.common.machines.CondenserBalance;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;
import theflogat.technomancy.compat.gtceu.GtceuPresence;

/**
 * In-world behaviour of the energy condenser: real Forge Energy in, real essentia into a real
 * TC4R container, and the four legacy defects that made the original dangerous.
 *
 * <p>Positions and faces are absolute, because the port's port masks are absolute world
 * directions while a GameTest structure may be placed at any rotation.</p>
 *
 * <p>Most checks drive {@link EnergyCondenserBlockEntity#serverTick} directly so a whole
 * transfer can be observed inside one game tick; {@link #condenserTurnsForgeEnergyIntoEssentia}
 * deliberately does not, so that the registration, the ticker wiring and the configured cost
 * are all exercised through the game's own loop.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CondenserGameTests {

    private static final String BATCH = "technom_condenser";
    private static final AspectId POTENTIA = EnergyCondenserBlockEntity.POTENTIA;
    private static final int PUSH_INTERVAL = 5;

    private CondenserGameTests() {}

    /**
     * The whole point of the block, through the real pipeline: Forge Energy pushed in through
     * the capability, the block's own ticker running it, essentia coming out. The books have
     * to balance to the Q — everything supplied is either still buffered, part-way through a
     * unit, or paid for a finished unit.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void condenserTurnsForgeEnergyIntoEssentia(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        IEnergyStorage port = energyPort(helper, condenser, Direction.UP);
        helper.assertTrue(port.canReceive(), "the condenser refuses Forge Energy on a face that must accept it");
        helper.assertTrue(!port.canExtract(), "the condenser must never hand energy back out");

        int perTick = (int) CondenserBalance.MAX_RATE_Q_PER_TICK;
        long[] supplied = {0};
        helper.startSequence()
                .thenExecuteFor(150, () -> supplied[0] += port.receiveEnergy(perTick, false))
                .thenExecute(() -> {
                    long cost = condenser.costQ();
                    int units = condenser.amount();
                    long stored = condenser.energy().ledger().stored();
                    long accounted = stored + condenser.unfinishedQ() + units * cost;
                    helper.assertTrue(units >= 2,
                            "150 ticks at " + perTick + " Q/t made only " + units + " units at " + cost + " Q each");
                    helper.assertTrue(supplied[0] == accounted,
                            "supplied " + supplied[0] + " Q but can only account for " + accounted
                                    + " (" + stored + " buffered + " + condenser.unfinishedQ() + " in progress + "
                                    + units + " x " + cost + ")");
                    Technomancy.LOGGER.info("GameTest condenser: {} Q of real Forge Energy became {} {}"
                                    + " with {} Q buffered and {} Q part-way through the next unit",
                            supplied[0], units, POTENTIA, stored, condenser.unfinishedQ());
                })
                .thenSucceed();
    }

    /** Essentia into a real TC4R warded jar, debited by exactly what the jar accepted. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void condenserFillsARealWardedJar(GameTestHelper helper) {
        BlockPos jarPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos pos = jarPos.above();
        WardedJarBlockEntity jar = placeJar(helper, jarPos);
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        openOutput(helper, pos, Direction.DOWN);

        condenser.store().add(POTENTIA, 20, false);
        int before = total(condenser, jar);
        tick(helper, pos, condenser, PUSH_INTERVAL);

        helper.assertTrue(jar.amount() == 20,
                "the jar holds " + jar.amount() + " " + jar.aspect() + " instead of 20 " + POTENTIA);
        helper.assertTrue(POTENTIA.equals(jar.aspect()), "the jar was filled with " + jar.aspect());
        helper.assertTrue(condenser.amount() == 0, "the condenser kept " + condenser.amount() + " it had given away");
        helper.assertTrue(total(condenser, jar) == before,
                "essentia was created or destroyed: " + before + " became " + total(condenser, jar));
        helper.succeed();
    }

    /**
     * Defect A-9, in the world. The receiver fills up part-way through the transfer, which is
     * exactly the case the original got backwards: it would have kept the whole buffer (and so
     * duplicated the part the jar took) or zeroed it (destroying the part the jar refused).
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void aReceiverFillingUpNeitherDuplicatesNorDestroys(GameTestHelper helper) {
        BlockPos jarPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos pos = jarPos.above();
        WardedJarBlockEntity jar = placeJar(helper, jarPos);
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        openOutput(helper, pos, Direction.DOWN);

        jar.addToContainer(POTENTIA, 60);
        condenser.store().add(POTENTIA, 20, false);
        int before = total(condenser, jar);
        helper.assertTrue(before == 80, "the fixture holds " + before + " instead of 80");

        tick(helper, pos, condenser, PUSH_INTERVAL);
        helper.assertTrue(jar.amount() == WardedJarBlockEntity.CAPACITY,
                "the jar should have filled to " + WardedJarBlockEntity.CAPACITY + ", it holds " + jar.amount());
        helper.assertTrue(condenser.amount() == 80 - WardedJarBlockEntity.CAPACITY,
                "the condenser kept " + condenser.amount() + " instead of " + (80 - WardedJarBlockEntity.CAPACITY));
        helper.assertTrue(total(condenser, jar) == before, "a partial transfer changed the total");

        // Now the receiver is full: another push must move nothing and change nothing.
        tick(helper, pos, condenser, PUSH_INTERVAL);
        helper.assertTrue(total(condenser, jar) == before, "a refused transfer changed the total");
        helper.assertTrue(condenser.amount() == 16 && jar.amount() == WardedJarBlockEntity.CAPACITY,
                "a refused transfer moved essentia after all");
        Technomancy.LOGGER.info("GameTest condenser: a jar filling up mid-transfer kept the total at {} units",
                before);
        helper.succeed();
    }

    /** Three-state redstone control, both directions of it. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void redstoneGatingStopsAndStartsProduction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos lever = pos.north();
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.SOUTH);
        helper.assertTrue(condenser.redstoneMode() == RedstoneMode.LOW,
                "the condenser must default to running without a signal, it is " + condenser.redstoneMode());

        energyPort(helper, condenser, Direction.UP).receiveEnergy((int) CondenserBalance.ENERGY_CAPACITY_Q, false);
        long full = condenser.energy().ledger().stored();
        helper.assertTrue(full > 0, "the buffer did not take any energy");

        tick(helper, pos, condenser, 3);
        long unpowered = condenser.energy().ledger().stored();
        helper.assertTrue(unpowered < full, "an unpowered LOW condenser did not convert anything");
        helper.assertTrue(condenser.isWorking(), "a converting condenser does not report itself as working");
        helper.assertTrue(condenser.progress() > 0.0F && condenser.progress() < 1.0F,
                "the progress readout is " + condenser.progress() + " part-way through a unit");

        level.setBlockAndUpdate(lever, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(level.hasNeighborSignal(pos), "the redstone block does not power the condenser");
        long progress = condenser.unfinishedQ();
        tick(helper, pos, condenser, 10);
        helper.assertTrue(condenser.energy().ledger().stored() == unpowered,
                "a powered LOW condenser spent " + (unpowered - condenser.energy().ledger().stored()) + " Q anyway");
        helper.assertTrue(condenser.unfinishedQ() == progress, "a gated condenser still made progress");
        helper.assertTrue(!condenser.isWorking(), "a gated condenser still reports itself as working");

        // The same signal now starts it, which is what makes this a mode and not a lockout.
        condenser.setRedstoneMode(RedstoneMode.HIGH);
        tick(helper, pos, condenser, 3);
        helper.assertTrue(condenser.energy().ledger().stored() < unpowered,
                "a powered HIGH condenser did not convert anything");
        helper.assertTrue(condenser.isWorking(), "a running HIGH condenser does not report itself as working");

        // A full output buffer is the other reason to stop, and it must cost nothing either.
        condenser.store().add(POTENTIA, CondenserBalance.ESSENTIA_CAPACITY, false);
        long beforeFull = condenser.energy().ledger().stored();
        long progressWhenFull = condenser.unfinishedQ();
        tick(helper, pos, condenser, 10);
        helper.assertTrue(condenser.energy().ledger().stored() == beforeFull,
                "a full condenser burned " + (beforeFull - condenser.energy().ledger().stored()) + " Q for nothing");
        helper.assertTrue(condenser.unfinishedQ() == progressWhenFull, "a full condenser still made progress");
        helper.assertTrue(!condenser.isWorking(), "a full condenser reports itself as working");
        Technomancy.LOGGER.info("GameTest condenser: redstone gating holds in both directions and a full"
                + " buffer spends nothing");
        helper.succeed();
    }

    /**
     * Defect A-10: the original's {@code doesContainerAccept} claimed it took essentia while
     * {@code addToContainer} threw everything away. Here the claim and the behaviour are the
     * same answer on every face and through the validated API.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void noFaceAcceptsEssentiaAndTheClaimMatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        condenser.store().add(POTENTIA, 10, false);

        for (Direction face : Direction.values()) {
            helper.assertTrue(!condenser.canInputFrom(face), "the condenser claims to accept input from " + face);
            helper.assertTrue(condenser.addEssentia(POTENTIA, 8, face, EssentiaTransferMode.EXECUTE) == 0,
                    "addEssentia accepted something on " + face);
            helper.assertTrue(EssentiaApi.add(level, condenser, POTENTIA, 8, face, EssentiaTransferMode.EXECUTE) == 0,
                    "EssentiaApi.add moved essentia into the condenser through " + face);
            helper.assertTrue(condenser.suctionAmount(face) == 0, "the condenser advertised suction on " + face);
            helper.assertTrue(condenser.suctionType(face) == null, "the condenser advertised a wanted type on " + face);
        }
        helper.assertTrue(condenser.amount() == 10,
                "the buffer changed to " + condenser.amount() + " while refusing everything");
        helper.assertTrue(condenser.minimumSuction() == 0, "anything with suction must be able to drain it");

        // A closed face is not an endpoint at all; an open one offers exactly what is stored.
        for (Direction face : Direction.values()) {
            helper.assertTrue(!condenser.isConnectable(face), "a fresh condenser is connectable on " + face);
            helper.assertTrue(condenser.essentiaAmount(face) == 0, "a closed face offered " + face);
        }
        openOutput(helper, pos, Direction.EAST);
        helper.assertTrue(condenser.isConnectable(Direction.EAST) && condenser.canOutputTo(Direction.EAST),
                "switching a face on did not make it an endpoint");
        helper.assertTrue(condenser.essentiaAmount(Direction.EAST) == 10, "an open face must offer what is stored");
        helper.assertTrue(condenser.takeEssentia(POTENTIA, 100, Direction.EAST, EssentiaTransferMode.SIMULATE) == 10,
                "takeEssentia must report the real amount, not the request");
        helper.assertTrue(condenser.amount() == 10, "a simulated take moved essentia");
        helper.assertTrue(condenser.takeEssentia(POTENTIA, 100, Direction.WEST, EssentiaTransferMode.EXECUTE) == 0,
                "a closed face gave essentia away");
        helper.succeed();
    }

    /**
     * Defect A-11: the original accepted energy from every direction with no direction
     * semantics at all, and its {@code extractEnergy} was open to anyone. Input on six faces
     * is kept; output on none of them is now structural.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyFaceTakesEnergyAndNoFaceGivesItBack(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);

        for (Direction face : Direction.values()) {
            IEnergyStorage port = energyPort(helper, condenser, face);
            helper.assertTrue(port.canReceive(), face + " does not accept energy");
            helper.assertTrue(!port.canExtract(), face + " offers to give energy back");
            helper.assertTrue(port.extractEnergy(1000, false) == 0, face + " let energy out");
        }
        IEnergyStorage unsided = condenser.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
        helper.assertTrue(unsided != null, "probes ask for the unsided view");
        helper.assertTrue(!unsided.canReceive() && !unsided.canExtract(),
                "an unsided query must not carry transfer rights");
        helper.assertTrue(unsided.receiveEnergy(1000, false) == 0, "the unsided view moved energy");

        IEnergyStorage north = energyPort(helper, condenser, Direction.NORTH);
        helper.assertTrue(north.receiveEnergy(5000, true) == 5000, "simulation must report what would fit");
        helper.assertTrue(condenser.energy().ledger().stored() == 0, "simulation moved energy");
        helper.assertTrue(north.receiveEnergy(5000, false) == 5000, "a real insert was refused");
        helper.assertTrue(condenser.energy().ledger().stored() == 5000,
                "the ledger holds " + condenser.energy().ledger().stored() + " after a 5000 Q insert");
        helper.assertTrue(north.extractEnergy(5000, false) == 0, "the buffer could be drained back out");
        helper.assertTrue(condenser.energy().ledger().stored() == 5000, "a refused extraction still moved energy");
        helper.succeed();
    }

    /**
     * Defect A-12: a real crash. The original null-checked the block entity only on the
     * sneaking path and dereferenced it two lines later regardless, so right-clicking a
     * condenser whose block entity was gone threw.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void interactingWithoutABlockEntityDoesNotThrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnergyCondenserBlock block = (EnergyCondenserBlock) TechnomBlocks.ENERGY_CONDENSER.get();
        BlockState state = block.defaultBlockState();
        Player player = helper.makeMockSurvivalPlayer();

        // Air, and a foreign block entity: neither can supply a condenser, and the branch that
        // used to crash is the non-sneaking one with something in hand.
        BlockPos air = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos barrel = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());

        for (BlockPos target : new BlockPos[] {air, barrel}) {
            for (ItemStack held : new ItemStack[] {ItemStack.EMPTY,
                    new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.REDSTONE)}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.setShiftKeyDown(false);
                helper.assertTrue(use(block, state, level, target, player) == InteractionResult.PASS,
                        "a condenser without a block entity should pass, not act, at " + target);
                player.setShiftKeyDown(true);
                helper.assertTrue(use(block, state, level, target, player) == InteractionResult.PASS,
                        "sneaking on a condenser without a block entity should pass at " + target);
            }
        }
        player.setShiftKeyDown(false);
        helper.succeed();
    }

    /** The two interactions that have no GUI: face switches and the three programming items. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void sneakClickSwitchesFacesAndProgrammingItemsSetTheMode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        EnergyCondenserBlock block = (EnergyCondenserBlock) TechnomBlocks.ENERGY_CONDENSER.get();
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        player.setShiftKeyDown(true);
        use(block, level.getBlockState(pos), level, pos, player, Direction.EAST);
        helper.assertTrue(condenser.canOutputTo(Direction.EAST), "sneak-clicking a face did not switch it on");
        use(block, level.getBlockState(pos), level, pos, player, Direction.EAST);
        helper.assertTrue(!condenser.canOutputTo(Direction.EAST), "sneak-clicking again did not switch it off");

        // The front is never an output; that invariant is what keeps the front texture and the
        // vent texture from both being asked for on one face.
        use(block, level.getBlockState(pos), level, pos, player, Direction.NORTH);
        helper.assertTrue(!condenser.canOutputTo(Direction.NORTH), "the facing face was switched on");

        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.REDSTONE, 4));
        use(block, level.getBlockState(pos), level, pos, player, Direction.EAST);
        helper.assertTrue(condenser.redstoneMode() == RedstoneMode.HIGH,
                "redstone dust did not select HIGH, the mode is " + condenser.redstoneMode());
        helper.assertTrue(condenser.isRedstoneModified(), "the machine did not record that it was programmed");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3,
                "programming did not cost one item");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GUNPOWDER));
        use(block, level.getBlockState(pos), level, pos, player, Direction.EAST);
        helper.assertTrue(condenser.redstoneMode() == RedstoneMode.NONE, "gunpowder did not select NONE");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.REDSTONE)),
                "the previous programming item was not handed back");
        helper.succeed();
    }

    /**
     * The block has to be harvestable at all: {@code requiresCorrectToolForDrops} without a
     * tool tag, or a loot table the data pack never loaded, both make it impossible to get the
     * block back, and neither shows up as an error anywhere.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theBlockIsMineableAndDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EnergyCondenserBlockEntity condenser = place(helper, pos, Direction.NORTH);
        BlockState state = level.getBlockState(pos);
        Player player = helper.makeMockSurvivalPlayer();

        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
                "the condenser needs a correct tool but is in no tool tag, so it can never be harvested");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        helper.assertTrue(player.hasCorrectToolForDrops(state), "a pickaxe is not the correct tool");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!player.hasCorrectToolForDrops(state), "a bare hand should not harvest a metal machine");

        List<ItemStack> drops = Block.getDrops(state, level, pos, condenser, player,
                new ItemStack(Items.STONE_PICKAXE));
        helper.assertTrue(drops.size() == 1,
                "the loot table produced " + drops.size() + " stacks; a missing table produces none");
        helper.assertTrue(drops.get(0).is(TechnomBlocks.ENERGY_CONDENSER.get().asItem()),
                "the condenser dropped " + drops.get(0));
        helper.succeed();
    }

    /** The optional GTCEu half: the condenser runs off native EU packets, not only off FE. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void condenserAcceptsNativeEuPackets(GameTestHelper helper) {
        if (!GtceuPresence.isLoaded()) {
            Technomancy.LOGGER.info("GameTest condenserAcceptsNativeEuPackets skipped: GTCEu is not on this runtime");
            helper.succeed();
            return;
        }
        CondenserGtceuChecks.euInput(helper);
    }

    // ---- fixtures ----

    static EnergyCondenserBlockEntity place(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.getLevel().setBlockAndUpdate(pos, TechnomBlocks.ENERGY_CONDENSER.get().defaultBlockState()
                .setValue(EnergyCondenserBlock.FACING, facing));
        if (!(helper.getLevel().getBlockEntity(pos) instanceof EnergyCondenserBlockEntity condenser)) {
            throw new IllegalStateException("no condenser block entity at " + pos);
        }
        return condenser;
    }

    private static WardedJarBlockEntity placeJar(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, TCBlocks.WARDED_JAR.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof WardedJarBlockEntity jar)) {
            throw new IllegalStateException("no warded jar block entity at " + pos);
        }
        return jar;
    }

    private static void openOutput(GameTestHelper helper, BlockPos pos, Direction face) {
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.getLevel().setBlockAndUpdate(pos, state.setValue(EnergyCondenserBlock.output(face), true));
    }

    private static IEnergyStorage energyPort(GameTestHelper helper, EnergyCondenserBlockEntity condenser,
            Direction face) {
        IEnergyStorage port = condenser.getCapability(ForgeCapabilities.ENERGY, face).orElse(null);
        helper.assertTrue(port != null, "no Forge Energy capability on " + face);
        return port;
    }

    static void tick(GameTestHelper helper, BlockPos pos, EnergyCondenserBlockEntity condenser, int times) {
        for (int i = 0; i < times; i++) {
            EnergyCondenserBlockEntity.serverTick(helper.getLevel(), pos,
                    helper.getLevel().getBlockState(pos), condenser);
        }
    }

    private static int total(EnergyCondenserBlockEntity condenser, WardedJarBlockEntity jar) {
        return condenser.amount() + jar.amount();
    }

    private static InteractionResult use(EnergyCondenserBlock block, BlockState state, ServerLevel level,
            BlockPos pos, Player player) {
        return use(block, state, level, pos, player, Direction.UP);
    }

    private static InteractionResult use(EnergyCondenserBlock block, BlockState state, ServerLevel level,
            BlockPos pos, Player player, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }
}
