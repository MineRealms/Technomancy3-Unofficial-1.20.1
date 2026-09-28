package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.tiles.dynamo.EssentiaDynamoBlockEntity;

/**
 * The essentia dynamo in a real world: a Thaumcraft warded jar feeds it, it produces energy, a
 * real Forge Energy receiver takes it, and nothing is created or destroyed on the way.
 *
 * <p>The conservation checks are written against measured quantities rather than tick counts, so
 * they assert an exact identity instead of a number that depends on when the block entity's
 * first tick happened to land.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EssentiaDynamoGameTests {

    private static final String BATCH = "technom_dynamo";

    private EssentiaDynamoGameTests() {}

    /**
     * The fuel table is a data pack, so an empty one here would mean the reload listener was
     * never installed - and a dynamo would run forever on essentia worth nothing.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theFuelTableIsLoadedFromTheDataPack(GameTestHelper helper) {
        helper.assertFalse(EssentiaFuelLoader.table().isEmpty(),
                "the essentia fuel table is empty; AddReloadListenerEvent never reached the loader");
        helper.assertTrue(EssentiaFuelLoader.table().listedAspects().size() == 44,
                "the table lists " + EssentiaFuelLoader.table().listedAspects().size()
                        + " aspects instead of the 44 the shipped file defines");
        helper.assertTrue(EssentiaFuelLoader.table().fallback() == 25,
                "fallback is " + EssentiaFuelLoader.table().fallback());
        helper.succeed();
    }

    /** A server reload publishes a fresh immutable fuel table for already-running machines. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void theFuelTableIsReplacedOnServerReload(GameTestHelper helper) {
        EssentiaFuelTable before = EssentiaFuelLoader.table();
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server != null, "the GameTest level has no server");

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withPermission(4), "reload");

        helper.runAfterDelay(60, () -> {
            EssentiaFuelTable after = EssentiaFuelLoader.table();
            helper.assertTrue(after != before, "server reload did not publish a new fuel table");
            helper.assertTrue(after.listedAspects().size() == 44,
                    "reload produced " + after.listedAspects().size() + " fuel entries instead of 44");
            helper.assertTrue(after.fallback() == 25,
                    "reload changed the fuel fallback to " + after.fallback());
            helper.succeed();
        });
    }

    /** The port's face rules, asserted against the contract rather than against the old code. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void faceRulesAgreeAndTheOutputFaceCarriesOnlyEnergy(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, false);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();
        Direction facing = dynamo.facing();
        helper.assertTrue(facing == Direction.UP, "the dynamo was placed facing " + facing);

        // Give it something to hold, or every "nothing comes out" assertion below would pass for
        // the wrong reason.
        helper.assertTrue(dynamo.addEssentia(DynamoChain.IGNIS, 10, Direction.DOWN,
                        EssentiaTransferMode.EXECUTE) == 10,
                "addEssentia must return what it accepted, not what was left over");
        helper.assertTrue(dynamo.visibleAspects().amounts().get(DynamoChain.IGNIS) == 10,
                "the fuel cache is not visible to goggles and probes");

        for (Direction face : Direction.values()) {
            boolean input = dynamo.canInputFrom(face);
            boolean connectable = dynamo.isConnectable(face);
            // Defect A-7: canInputFrom was a constant true while isConnectable excluded the
            // energy face, so a tube trusting the former pushed essentia into the power output.
            helper.assertTrue(input == connectable, face + ": canInputFrom " + input
                    + " disagrees with isConnectable " + connectable);
            helper.assertTrue(input == (face != facing),
                    face + ": essentia input is " + input + " but the output face is " + facing);
            helper.assertFalse(dynamo.canOutputTo(face), face + ": a dynamo must not give essentia back");
            helper.assertTrue(dynamo.takeEssentia(DynamoChain.IGNIS, 1, face,
                            EssentiaTransferMode.EXECUTE) == 0,
                    face + ": takeEssentia handed fuel back out");
            helper.assertTrue(dynamo.availableEssentia(DynamoChain.IGNIS, face) == 0,
                    face + ": advertised extractable essentia it will not hand over");
            // Must be 0 even though the cache holds 10: TC4R's buffer tube checks essentiaAmount
            // before canOutputTo and returns without scanning its other faces, so a non-zero
            // answer here stalls it permanently.
            helper.assertTrue(dynamo.essentiaAmount(face) == 0,
                    face + ": reported " + dynamo.essentiaAmount(face) + " units available on a"
                            + " face that gives nothing");

            IEnergyStorage view = dynamo.getCapability(ForgeCapabilities.ENERGY, face).orElse(null);
            helper.assertTrue(view != null, face + ": no Forge Energy view at all");
            helper.assertFalse(view.canReceive(), face + ": a dynamo must never accept energy");
            helper.assertTrue(view.receiveEnergy(1000, false) == 0,
                    face + ": receiveEnergy took energy anyway");
            helper.assertTrue(view.canExtract() == (face == facing),
                    face + ": canExtract is " + view.canExtract() + " on a " + facing + "-facing dynamo");
        }
        helper.assertTrue(dynamo.minimumSuction() == EssentiaDynamoBlockEntity.SUCTION,
                "minimumSuction is " + dynamo.minimumSuction());
        // The buffer is empty, so it wants essentia on every essentia face and nothing on the
        // output face.
        helper.assertTrue(dynamo.suctionAmount(Direction.DOWN) == EssentiaDynamoBlockEntity.SUCTION,
                "suction on an input face is " + dynamo.suctionAmount(Direction.DOWN));
        helper.assertTrue(dynamo.suctionAmount(facing) == 0,
                "the output face advertises suction " + dynamo.suctionAmount(facing));
        Technomancy.LOGGER.info("GameTest dynamo faces: {}-facing dynamo accepts essentia on the other"
                + " five faces only, canInputFrom == isConnectable on all six, canOutputTo and"
                + " takeEssentia are 0 everywhere, suction {}/{} and FE extract only on {}",
                facing, dynamo.suctionAmount(Direction.DOWN), dynamo.minimumSuction(), facing);
        helper.succeed();
    }

    /** A real Thaumcraft jar hands essentia over, and the dynamo turns it into energy. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void aWardedJarFeedsTheDynamoWhichPowersARealConsumer(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, true);
        long perUnit = chain.energyPerUnit();
        helper.assertTrue(perUnit > 0, "one unit of ignis is worth " + perUnit + " Q here");

        helper.runAfterDelay(80, () -> {
            int pulled = DynamoChain.JAR_CAPACITY - chain.jarAmount();
            helper.assertTrue(pulled > 0, "the dynamo pulled nothing out of the warded jar");
            helper.assertTrue(chain.delivered() > 0,
                    "the dynamo generated " + chain.dynamoEnergy() + " Q but delivered none of it");

            // One unit every five ticks is the port's throttle, matching the tube's own rhythm;
            // over 80 ticks that is at most 16 and, allowing for start-up, at least a handful.
            helper.assertTrue(pulled <= 80 / 5 + 1, "pulled " + pulled + " units in 80 ticks");
            helper.assertTrue(pulled >= 8, "only pulled " + pulled + " units in 80 ticks");

            // Generation runs at the base rate, so the receiver must have most of it by now.
            long produced = chain.dynamoEnergy() + chain.delivered();
            helper.assertTrue(produced >= 60 * EssentiaDynamoBlockEntity.BASE_RATE,
                    "produced only " + produced + " Q in 80 ticks at "
                            + EssentiaDynamoBlockEntity.BASE_RATE + " Q/t");
            helper.assertTrue(chain.delivered() >= produced - EssentiaDynamoBlockEntity.MAX_OUTPUT,
                    "the dynamo is hoarding: " + chain.dynamoEnergy() + " Q left in a buffer that can"
                            + " push " + EssentiaDynamoBlockEntity.MAX_OUTPUT + " Q/t");
            Technomancy.LOGGER.info("GameTest dynamo chain: in 80 ticks a real warded jar gave up {}"
                    + " units of ignis ({} Q each), the dynamo produced {} Q and a real Forge Energy"
                    + " receiver took {} Q of it", pulled, perUnit, produced, chain.delivered());
            helper.succeed();
        });
    }

    /**
     * Essentia and energy are both accounted for across the whole chain.
     *
     * <p>Two identities, both exact: what left the jar is either in the dynamo's buffer or was
     * burned, and what was burned is worth exactly the energy that exists plus the energy still
     * banked as fuel.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void essentiaAndEnergyAreConservedAcrossTheChain(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, true);
        long perUnit = chain.energyPerUnit();

        helper.runAfterDelay(120, () -> {
            int left = chain.jarAmount();
            int cached = chain.dynamoAmount();
            int burned = DynamoChain.JAR_CAPACITY - left - cached;
            helper.assertTrue(burned > 0, "nothing was burned, so there is nothing to account for");

            long energy = chain.dynamoEnergy() + chain.delivered();
            long banked = chain.dynamo().fuel();
            helper.assertTrue(burned * perUnit == energy + banked,
                    "burned " + burned + " units worth " + burned * perUnit + " Q, but the chain holds "
                            + energy + " Q plus " + banked + " Q of banked fuel");

            // And the essentia side on its own: the jar cannot have lost more than exists.
            helper.assertTrue(left >= 0 && cached >= 0 && left + cached + burned == DynamoChain.JAR_CAPACITY,
                    "essentia does not add up: " + left + " in the jar, " + cached + " cached, "
                            + burned + " burned");
            Technomancy.LOGGER.info("GameTest dynamo conservation: jar {} + cache {} + burned {} = {}"
                    + " units; burned x {} Q = {} Q = {} Q produced + {} Q still banked as fuel",
                    left, cached, burned, DynamoChain.JAR_CAPACITY, perUnit, burned * perUnit,
                    energy, banked);
            helper.assertTrue(cached <= EssentiaDynamoBlockEntity.ESSENTIA_CAPACITY,
                    "the dynamo cached " + cached + " units over its "
                            + EssentiaDynamoBlockEntity.ESSENTIA_CAPACITY + " limit");
            helper.succeed();
        });
    }

    /**
     * Redstone gating stops generation <em>and</em> the essentia pull.
     *
     * <p>Defect A-18: the original left {@code fill()} outside the redstone check, so a dynamo a
     * player had switched off went on emptying the tube beside it into a buffer it would never
     * burn, starving everything downstream.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void redstoneGatingStopsGenerationAndTheEssentiaPull(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, true);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();
        helper.assertTrue(dynamo.redstone().mode() == RedstoneMode.NONE,
                "the dynamo should ignore redstone out of the box, not start on "
                        + dynamo.redstone().mode());
        dynamo.redstone().set(RedstoneMode.HIGH);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(chain.jarAmount() == DynamoChain.JAR_CAPACITY,
                    "a dynamo waiting for a redstone signal still took "
                            + (DynamoChain.JAR_CAPACITY - chain.jarAmount()) + " units out of the jar");
            helper.assertTrue(chain.dynamoAmount() == 0,
                    "it cached " + chain.dynamoAmount() + " units while switched off");
            helper.assertTrue(chain.dynamoEnergy() == 0 && chain.delivered() == 0,
                    "it produced " + (chain.dynamoEnergy() + chain.delivered()) + " Q while switched off");
            helper.assertFalse(chain.dynamo().isWorking(), "it reports itself as working while switched off");

            chain.power();
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(chain.jarAmount() < DynamoChain.JAR_CAPACITY,
                        "a powered dynamo pulled nothing");
                helper.assertTrue(chain.delivered() > 0, "a powered dynamo delivered nothing");
                Technomancy.LOGGER.info("GameTest dynamo redstone: 60 ticks on HIGH with no signal"
                        + " took 0 essentia and produced 0 Q; 60 ticks after a redstone block it had"
                        + " pulled {} units and delivered {} Q",
                        DynamoChain.JAR_CAPACITY - chain.jarAmount(), chain.delivered());
                helper.assertTrue(chain.dynamo().isWorking(), "a burning dynamo does not report it");
                helper.succeed();
            });
        });
    }

    /**
     * A full energy buffer neither takes essentia nor burns banked fuel.
     *
     * <p>Defect A-14: the original asked for fuel with a clamped rate of 0, was handed a whole
     * fuel value for {@code ceil(0) == 0} units of essentia, and then spent it one tick at a
     * time producing nothing.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void aFullBufferTakesNoEssentiaAndWastesNoFuel(GameTestHelper helper) {
        // No receiver: nothing may drain the buffer, or it would make room to work again.
        DynamoChain chain = DynamoChain.place(helper, false);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();

        helper.runAfterDelay(40, () -> {
            // Let it run first, so there is banked fuel to lose, then fill the buffer.
            int cachedBefore = chain.dynamoAmount();
            int burnedBefore = DynamoChain.JAR_CAPACITY - chain.jarAmount() - cachedBefore;
            helper.assertTrue(cachedBefore + burnedBefore > 0, "the dynamo never pulled anything to burn");
            dynamo.energy().ledger().generate(EssentiaDynamoBlockEntity.ENERGY_CAPACITY);
            helper.assertTrue(dynamo.energy().ledger().space() == 0,
                    "the buffer is not full: " + dynamo.energy().ledger().space() + " Q of room left");
            long bankedBefore = dynamo.fuel();
            helper.assertTrue(bankedBefore > 0, "no banked fuel, so there is nothing to protect");

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(dynamo.fuel() == bankedBefore,
                        "banked fuel fell from " + bankedBefore + " to " + dynamo.fuel()
                                + " Q while the buffer was full");
                // Topping the essentia cache up continues on purpose - that is a fuel tank, and
                // it is bounded by the 64-unit limit. What must not happen is another charge
                // being taken out of it, which is what "burned" counts.
                int burnedAfter = DynamoChain.JAR_CAPACITY - chain.jarAmount() - chain.dynamoAmount();
                helper.assertTrue(burnedAfter == burnedBefore,
                        "essentia burned went from " + burnedBefore + " to " + burnedAfter
                                + " units with a full buffer and nothing to show for it");
                helper.assertTrue(chain.dynamoAmount() >= cachedBefore,
                        "cached essentia fell from " + cachedBefore + " to " + chain.dynamoAmount());
                helper.assertTrue(dynamo.energy().ledger().stored()
                                == EssentiaDynamoBlockEntity.ENERGY_CAPACITY,
                        "the buffer holds " + dynamo.energy().ledger().stored() + " Q");
                Technomancy.LOGGER.info("GameTest dynamo full buffer: 60 ticks at {} of {} Q burned no"
                        + " fuel ({} Q banked throughout) and bought no charge ({} units burned"
                        + " throughout)", dynamo.energy().ledger().stored(),
                        EssentiaDynamoBlockEntity.ENERGY_CAPACITY, bankedBefore, burnedBefore);
                helper.succeed();
            });
        });
    }

    /**
     * The potency gem is throughput only: four times the rate for four times the essentia.
     *
     * <p>Measured in the world rather than only in the unit test, because it is the wiring
     * between the rate and the units per charge that has to agree, not just the arithmetic.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void thePotencyGemQuadruplesThroughputAndNotEfficiency(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, true);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();
        long perUnit = chain.energyPerUnit();
        helper.assertTrue(dynamo.ratePerTick() == EssentiaDynamoBlockEntity.BASE_RATE
                        && dynamo.unitsPerCharge() == 1,
                "an un-upgraded dynamo runs at " + dynamo.ratePerTick() + " Q/t on "
                        + dynamo.unitsPerCharge() + " units per charge");
        helper.assertTrue(dynamo.setBoosted(true), "the potency gem would not install");
        helper.assertTrue(dynamo.ratePerTick() == EssentiaDynamoBlockEntity.BOOSTED_RATE
                        && dynamo.unitsPerCharge() == EssentiaDynamoBlockEntity.BOOSTED_UNITS,
                "a boosted dynamo runs at " + dynamo.ratePerTick() + " Q/t on "
                        + dynamo.unitsPerCharge() + " units per charge");

        helper.runAfterDelay(120, () -> {
            int burned = DynamoChain.JAR_CAPACITY - chain.jarAmount() - chain.dynamoAmount();
            helper.assertTrue(burned > 0 && burned % EssentiaDynamoBlockEntity.BOOSTED_UNITS == 0,
                    "a boosted dynamo burned " + burned + " units, which is not a whole number of"
                            + " four-unit charges");
            long energy = chain.dynamoEnergy() + chain.delivered() + chain.dynamo().fuel();
            helper.assertTrue(burned * perUnit == energy,
                    "boosted: " + burned + " units worth " + burned * perUnit + " Q produced " + energy);
            // Four times the rate is what a gem is for; the receiver is far faster than 320 Q/t.
            helper.assertTrue(chain.delivered() >= 80 * EssentiaDynamoBlockEntity.BASE_RATE * 2,
                    "a boosted dynamo delivered only " + chain.delivered() + " Q in 120 ticks");
            Technomancy.LOGGER.info("GameTest dynamo potency gem: {} Q/t on {} units per charge burned"
                    + " {} units worth {} Q and produced exactly {} Q, delivering {} Q in 120 ticks",
                    dynamo.ratePerTick(), dynamo.unitsPerCharge(), burned, burned * perUnit, energy,
                    chain.delivered());
            helper.succeed();
        });
    }

    /**
     * The realistic wiring: jar, two lengths of Thaumcraft essentia tube, dynamo.
     *
     * <p>This is the path the suction rules actually govern. Each tube passes the dynamo's
     * advertised 128 upstream with one point of decay and holds a single unit at a time, so if
     * the dynamo's suction or minimum were wrong the chain would simply never move, with nothing
     * to show for it.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 300)
    public static void essentiaReachesTheDynamoThroughRealTubes(GameTestHelper helper) {
        // Two tubes and no receiver: jar, tube, tube, dynamo already fills the four air layers
        // the template has, and the dynamo's own buffer is enough to measure production.
        DynamoChain chain = DynamoChain.place(helper, false, 2);
        long perUnit = chain.energyPerUnit();
        helper.assertTrue(perUnit > 0, "one unit of ignis is worth " + perUnit + " Q here");

        helper.runAfterDelay(160, () -> {
            int cached = chain.dynamoAmount();
            int moved = DynamoChain.JAR_CAPACITY - chain.jarAmount();
            helper.assertTrue(moved > 0,
                    "nothing crossed two tubes in 160 ticks; the dynamo's suction of "
                            + EssentiaDynamoBlockEntity.SUCTION + " is not winning against them");
            helper.assertTrue(chain.dynamoEnergy() > 0, "no energy came out of the tube-fed dynamo");

            // Conservation still holds, but the units in flight inside the tubes are neither in
            // the jar nor in the dynamo, so they have to be accounted for separately.
            long energy = chain.dynamoEnergy() + chain.delivered() + chain.dynamo().fuel();
            int burned = (int) (energy / perUnit);
            helper.assertTrue(burned * perUnit == energy,
                    "energy " + energy + " Q is not a whole number of " + perUnit + " Q units");
            int inFlight = moved - cached - burned;
            helper.assertTrue(inFlight >= 0 && inFlight <= 2,
                    "the two tubes are holding " + inFlight + " units, and a tube holds one");
            Technomancy.LOGGER.info("GameTest dynamo tubes: through 2 essentia tubes in 160 ticks the"
                    + " jar gave up {} units, {} are cached, {} were burned into {} Q and {} are in"
                    + " flight inside the tubes", moved, cached, burned, energy, inFlight);
            helper.succeed();
        });
    }

    /**
     * The block can actually be mined for its item.
     *
     * <p>It asks for a correct tool, and a block that asks for one while belonging to no
     * {@code mineable} tag can never be harvested at all - a "registered but unobtainable"
     * failure that no amount of behaviour testing would notice.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theBlockCanBeMinedForItsItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, TechnomBlocks.ESSENTIA_DYNAMO.get().defaultBlockState());
        BlockState state = level.getBlockState(pos);

        helper.assertTrue(state.is(TechnomBlocks.ESSENTIA_DYNAMO.get()), "the dynamo was not placed");
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
                "the dynamo requires a correct tool but is in no mineable tag, so it can never drop");
        helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state),
                "a stone pickaxe is not accepted as a correct tool");
        helper.assertFalse(new ItemStack(Items.SHEARS).isCorrectToolForDrops(state),
                "shears count as a correct tool, so the tool requirement means nothing");

        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), null,
                new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(drops.size() == 1, "the loot table produced " + drops.size() + " stacks");
        helper.assertTrue(drops.get(0).is(TechnomBlocks.ESSENTIA_DYNAMO.get().asItem())
                        && drops.get(0).getCount() == 1,
                "the loot table produced " + drops.get(0));
        Technomancy.LOGGER.info("GameTest dynamo drops: mineable with a pickaxe and the loot table"
                + " yields exactly one {}", drops.get(0).getItem());
        helper.succeed();
    }

    /**
     * Turning the output moves both sets of face rules with it.
     *
     * <p>Defect A-19: the 1.7.10 wrench walked the six faces and stopped at the first neighbour
     * that would accept energy, refusing to turn at all when there was none, so a dynamo could
     * not be aimed before the machine it would feed existed. Here it always turns, and the
     * essentia faces and the energy capability follow.</p>
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void turningTheOutputMovesEveryFaceRule(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, false);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();
        Direction first = dynamo.facing();

        for (int turn = 1; turn <= Direction.values().length; turn++) {
            Direction before = chain.dynamo().facing();
            Direction after = chain.dynamo().cycleFacing();
            helper.assertTrue(after != before,
                    "turn " + turn + ": the dynamo refused to move off " + before
                            + " with no energy neighbour anywhere");
            EssentiaDynamoBlockEntity turned = chain.dynamo();
            helper.assertFalse(turned.canInputFrom(after),
                    "turn " + turn + ": the new output face " + after + " still accepts essentia");
            helper.assertTrue(turned.canInputFrom(before),
                    "turn " + turn + ": the old output face " + before + " did not become an essentia face");
            IEnergyStorage out = turned.getCapability(ForgeCapabilities.ENERGY, after).orElse(null);
            IEnergyStorage old = turned.getCapability(ForgeCapabilities.ENERGY, before).orElse(null);
            helper.assertTrue(out != null && out.canExtract(),
                    "turn " + turn + ": no energy output on the new face " + after);
            helper.assertTrue(old != null && !old.canExtract(),
                    "turn " + turn + ": the old face " + before + " still hands out energy");
        }
        helper.assertTrue(chain.dynamo().facing() == first,
                "six turns should return to " + first + ", not " + chain.dynamo().facing());
        Technomancy.LOGGER.info("GameTest dynamo rotation: six unconditional turns returned to {},"
                + " with the essentia faces and the Forge Energy view following each time", first);
        helper.succeed();
    }

    /** Everything a dynamo holds survives a save and load. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void savedStateIsRestoredVerbatim(GameTestHelper helper) {
        DynamoChain chain = DynamoChain.place(helper, false);
        EssentiaDynamoBlockEntity dynamo = chain.dynamo();
        dynamo.redstone().set(RedstoneMode.LOW);
        dynamo.setBoosted(true);

        helper.runAfterDelay(40, () -> {
            EssentiaDynamoBlockEntity before = chain.dynamo();
            long energy = before.energy().ledger().stored();
            long fuel = before.fuel();
            int essentia = before.store().total();
            helper.assertTrue(energy > 0 && fuel > 0 && essentia > 0,
                    "nothing worth saving yet: " + energy + " Q, " + fuel + " Q of fuel, "
                            + essentia + " units");

            CompoundTag saved = before.saveWithoutMetadata();
            EssentiaDynamoBlockEntity restored =
                    new EssentiaDynamoBlockEntity(chain.dynamoPos(), before.getBlockState());
            restored.load(saved);

            helper.assertTrue(restored.energy().ledger().stored() == energy,
                    "energy came back as " + restored.energy().ledger().stored() + " instead of " + energy);
            helper.assertTrue(restored.fuel() == fuel,
                    "banked fuel came back as " + restored.fuel() + " instead of " + fuel);
            helper.assertTrue(restored.store().total() == essentia
                            && restored.store().dominantAspect().equals(DynamoChain.IGNIS),
                    "essentia came back as " + restored.store().total() + " of "
                            + restored.store().dominantAspect());
            helper.assertTrue(restored.isBoosted(), "the potency gem was lost");
            helper.assertTrue(restored.redstone().mode() == RedstoneMode.LOW
                            && restored.redstone().isModified(),
                    "the redstone mode came back as " + restored.redstone().mode()
                            + " (modified " + restored.redstone().isModified() + ")");
            Technomancy.LOGGER.info("GameTest dynamo persistence: {} Q, {} Q of banked fuel, {} units of"
                    + " {}, the potency gem and redstone mode {} all survived a save and load",
                    energy, fuel, essentia, DynamoChain.IGNIS, RedstoneMode.LOW);
            helper.succeed();
        });
    }
}
