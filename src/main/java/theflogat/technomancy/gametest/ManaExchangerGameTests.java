package theflogat.technomancy.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.botania.ManaExchangerBlock;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.tiles.botania.ManaExchangerBlockEntity;
import vazkii.botania.api.mana.ManaPool;

/**
 * Mana Exchanger against a real Botania mana pool: the pool must sit directly on top, and a full
 * Q buffer buys one 1,000-mana / 1-bucket step per tick in either direction.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaExchangerGameTests {

    private static final String BATCH = "technom_botania";
    private static final BlockPos EXCHANGER = new BlockPos(2, 1, 2);
    private static final BlockPos POOL = new BlockPos(2, 2, 2);

    private ManaExchangerGameTests() {}

    private static ManaPool placePool(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation("botania", "mana_pool"));
        helper.setBlock(POOL, block);
        return (ManaPool) helper.getBlockEntity(POOL);
    }

    private static ManaExchangerBlockEntity placeExchanger(GameTestHelper helper, boolean out) {
        helper.setBlock(EXCHANGER, TechnomBlocks.MANA_EXCHANGER.get().defaultBlockState()
                .setValue(ManaExchangerBlock.OUT, out));
        return (ManaExchangerBlockEntity) helper.getBlockEntity(EXCHANGER);
    }

    /** Importing: 1,000 mana in the pool becomes one bucket, at 1,000 Q a step. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void drawsManaIntoTheTank(GameTestHelper helper) {
        ManaPool pool = placePool(helper);
        ManaExchangerBlockEntity machine = placeExchanger(helper, false);
        machine.energy().ledger().generate(ManaExchangerBlockEntity.ENERGY_CAPACITY);
        // The pool only knows its capacity after its first tick, so seed it once it has ticked.
        helper.runAfterDelay(2, () -> {
            pool.receiveMana(5_000);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(pool.getCurrentMana() == 0,
                        "pool should be empty, holds " + pool.getCurrentMana());
                helper.assertTrue(machine.tank().getFluidAmount() == 5,
                        "tank should hold 5 mB, holds " + machine.tank().getFluidAmount());
                helper.assertTrue(machine.energy().ledger().stored() == 5_000,
                        "5 operations should cost 5,000 Q, left " + machine.energy().ledger().stored());
                helper.succeed();
            });
        });
    }

    /** Exporting: one bucket becomes 1,000 mana in the pool. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 100)
    public static void pushesFluidIntoThePool(GameTestHelper helper) {
        ManaPool pool = placePool(helper);
        ManaExchangerBlockEntity machine = placeExchanger(helper, true);
        machine.tank().fill(new net.minecraftforge.fluids.FluidStack(
                theflogat.technomancy.common.registry.TechnomFluids.MANA.get(), 5),
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        machine.energy().ledger().generate(ManaExchangerBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pool.getCurrentMana() == 5_000,
                    "pool should hold 5,000 mana, holds " + pool.getCurrentMana());
            helper.assertTrue(machine.tank().getFluidAmount() == 0,
                    "tank should be empty, holds " + machine.tank().getFluidAmount());
            helper.assertTrue(machine.energy().ledger().stored() == 5_000,
                    "5 operations should cost 5,000 Q, left " + machine.energy().ledger().stored());
            helper.succeed();
        });
    }

    /** With nothing above it the exchanger is inactive and spends nothing. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 60)
    public static void needsAPoolOnTop(GameTestHelper helper) {
        ManaExchangerBlockEntity machine = placeExchanger(helper, false);
        machine.energy().ledger().generate(ManaExchangerBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(machine.getBlockState().getValue(ManaExchangerBlock.ACTIVE),
                    "an exchanger with no pool above is active");
            helper.assertTrue(machine.energy().ledger().stored() == ManaExchangerBlockEntity.ENERGY_CAPACITY,
                    "an idle exchanger spent energy: " + machine.energy().ledger().stored());
            helper.succeed();
        });
    }
}
