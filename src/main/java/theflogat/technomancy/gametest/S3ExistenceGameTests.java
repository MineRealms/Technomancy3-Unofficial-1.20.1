package theflogat.technomancy.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.technom.ExistenceGemItem;
import theflogat.technomancy.common.machines.existence.ExistenceConversion;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistencePylonBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceTier;

/**
 * S3 Existence: the fountain-to-pylon link that was missing, the gem that every Existence
 * machine is crafted from, and the crafting recipes that make the whole chain reachable in
 * survival. Written for the S3 batch run.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S3ExistenceGameTests {

    private static final String BATCH = "technom_s3_existence";

    private S3ExistenceGameTests() {
    }

    /**
     * A pylon can only be filled by an {@code IExistenceProducer}, so a pylon next to a fountain
     * holding power is the whole proof that the fountain is wired into the network.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void fountainFeedsThePylon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fountainPos = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos pylonPos = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlockAndUpdate(fountainPos, TechnomBlocks.EXISTENCE_FOUNTAIN.get().defaultBlockState());
        level.setBlockAndUpdate(pylonPos, TechnomBlocks.EXISTENCE_PYLON_BASIC.get().defaultBlockState());
        if (!(level.getBlockEntity(fountainPos) instanceof ExistenceFountainBlockEntity fountain)
                || !(level.getBlockEntity(pylonPos) instanceof ExistencePylonBlockEntity pylon)) {
            helper.fail("the fountain or the pylon has no block entity");
            return;
        }
        helper.runAfterDelay(20, () -> {
            S2StorageGameTests.check(helper, pylon.getPower() > 0,
                    "the pylon took nothing from the fountain, so the fountain is not a producer");
            S2StorageGameTests.check(helper, pylon.getPower() <= ExistenceTier.BASIC.rate(),
                    "a basic pylon held more than its own rate: " + pylon.getPower());
            Technomancy.LOGGER.info("GameTest existence: pylon took {} from a fountain holding {}",
                    pylon.getPower(), fountain.getPower());
            helper.succeed();
        });
    }

    /** The three tiers are 5, 25 and 125 per tick, and a pylon never holds more than its rate. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH, timeoutTicks = 200)
    public static void pylonTierRatesBoundTheirOwnBuffer(GameTestHelper helper) {
        S2StorageGameTests.check(helper, ExistenceTier.BASIC.rate() == 5, "basic rate");
        S2StorageGameTests.check(helper, ExistenceTier.ADVANCED.rate() == 25, "advanced rate");
        S2StorageGameTests.check(helper, ExistenceTier.COMPLEX.rate() == 125, "complex rate");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, TechnomBlocks.EXISTENCE_PYLON_COMPLEX.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof ExistencePylonBlockEntity pylon)) {
            helper.fail("no pylon block entity");
            return;
        }
        S2StorageGameTests.check(helper, pylon.getMaxRate() == ExistenceTier.COMPLEX.rate(), "complex pylon rate");
        pylon.addPower(1000);
        S2StorageGameTests.check(helper, pylon.getPower() == ExistenceTier.COMPLEX.rate(),
                "a pylon accepted more power than its tier can hold: " + pylon.getPower());
        helper.succeed();
    }

    /** The gem: empty from the table, filled by kills, capped, and glinted only when full. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void gemChargesAndCapsAtOneHundred(GameTestHelper helper) {
        ItemStack gem = new ItemStack(TechnomItems.EXISTENCE_GEM.get());
        S2StorageGameTests.check(helper, ExistenceGemItem.charge(gem) == 0, "a fresh gem is not empty");
        S2StorageGameTests.check(helper, !ExistenceGemItem.isFull(gem), "a fresh gem reads as full");
        S2StorageGameTests.check(helper, ExistenceGemItem.addCharge(gem, 25) == 25, "a villager's worth");
        S2StorageGameTests.check(helper, ExistenceGemItem.charge(gem) == 25, "charge not kept");
        S2StorageGameTests.check(helper, ExistenceGemItem.addCharge(gem, 1000) == 75, "the cap is 100");
        S2StorageGameTests.check(helper, ExistenceGemItem.isFull(gem), "a full gem does not read as full");
        S2StorageGameTests.check(helper, gem.getItem().isFoil(gem), "a full gem has no glint");
        // 1.7.10's own numbers: a villager is worth half of 50, an animal half of 5, a monster 1.
        S2StorageGameTests.check(helper, ExistenceConversion.getGem(new net.minecraft.world.entity.npc.Villager(
                net.minecraft.world.entity.EntityType.VILLAGER, helper.getLevel())) == 25, "villager gem value");
        S2StorageGameTests.check(helper, ExistenceConversion.getGem(new net.minecraft.world.entity.animal.Cow(
                net.minecraft.world.entity.EntityType.COW, helper.getLevel())) == 2, "animal gem value");
        helper.succeed();
    }

    /**
     * Nothing in the Existence chain is reachable unless it can be crafted. These are the recipes
     * the 1.7.10 {@code CraftingHandler} registered and the port had not.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void everyExistenceBlockIsCraftable(GameTestHelper helper) {
        String[] recipes = {
            "existence_gem", "existence_burner", "existence_dynamic_burner",
            "existence_crop_accelerator", "existence_harvester", "existence_sealer",
            "existence_pylon_basic", "existence_pylon_advanced", "existence_pylon_complex",
            "ritual_tome", "crystal_earth", "crystal_fire", "crystal_water", "crystal_light", "crystal_dark",
            "catalyst_earth", "catalyst_fire", "catalyst_water", "catalyst_light", "catalyst_dark"
        };
        for (String name : recipes) {
            boolean present = helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation(Technomancy.MOD_ID, name)).isPresent();
            S2StorageGameTests.check(helper, present, "no recipe registered: " + name);
        }
        helper.succeed();
    }
}
