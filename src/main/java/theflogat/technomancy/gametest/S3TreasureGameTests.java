package theflogat.technomancy.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.technom.TreasureEvents;
import theflogat.technomancy.common.items.technom.TreasureItem;
import theflogat.technomancy.common.items.technom.Treasures;
import theflogat.technomancy.common.player.Affinity;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.rituals.earth.RitualExtraction;

/**
 * S3 treasures: the combat half of {@code ItemTreasure}. A carrier only exists behind the
 * off-by-default {@code treasures} flag, so these drive the behaviour through
 * {@link TreasureEvents#applyWard} and {@link TreasureEvents#settleDeath} - the methods the event
 * listeners delegate to - rather than waiting on the one-in-fifty villager roll.
 *
 * <p>Every ward case comes in a pair: an unsealed carrier that must react, and a sealed one that
 * must not, so neither half can pass with the whole feature deleted.</p>
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class S3TreasureGameTests {

    private static final String BATCH = "technom_s3_treasure";
    private static final String SEALED = "seal";
    /** Nothing in these tests ticks, so "unmoved" is exact; this only guards against float drift. */
    private static final double STILL = 1.0E-6D;

    private S3TreasureGameTests() {
    }

    private static Villager villagerAt(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        BlockPos pos = helper.absolutePos(relative);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
        return villager;
    }

    private static Zombie zombieAt(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        BlockPos pos = helper.absolutePos(relative);
        zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(zombie);
        return zombie;
    }

    /** A villager carrying the named treasure, i.e. what the join handler produces. */
    private static Villager carrierOf(GameTestHelper helper, BlockPos relative, String treasure) {
        Villager villager = villagerAt(helper, relative);
        villager.getPersistentData().putString(RitualExtraction.TREASURE_TAG, treasure);
        return villager;
    }

    private static LivingHurtEvent meleeFrom(ServerLevel level, Villager victim, Zombie attacker) {
        return new LivingHurtEvent(victim, level.damageSources().mobAttack(attacker), 5.0F);
    }

    /** Whether any treasure item entity lies within a few blocks of the given position. */
    private static boolean droppedTreasureNear(ServerLevel level, BlockPos around) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(4.0D))) {
            if (item.getItem().getItem() instanceof TreasureItem) {
                return true;
            }
        }
        return false;
    }

    private static Affinity affinityOf(String name) {
        TreasureItem item = Treasures.item(name);
        return item == null ? null : item.affinity();
    }

    /** Fire gem: a fire hit is swallowed, and whoever landed it catches fire. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void fireGemCarrierSwallowsFireAndIgnitesTheAttacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager carrier = carrierOf(helper, new BlockPos(1, 1, 1), Treasures.FIRE_GEM);
        Zombie attacker = zombieAt(helper, new BlockPos(3, 1, 1));

        LivingHurtEvent melee = meleeFrom(level, carrier, attacker);
        TreasureEvents.applyWard(carrier, melee);
        S2StorageGameTests.check(helper, !melee.isCanceled(), "a melee hit is not fire damage, so it stands");
        S2StorageGameTests.check(helper, attacker.isOnFire(), "the fire gem did not set its attacker alight");

        LivingHurtEvent fire = new LivingHurtEvent(carrier, level.damageSources().inFire(), 5.0F);
        TreasureEvents.applyWard(carrier, fire);
        S2StorageGameTests.check(helper, fire.isCanceled(), "the fire gem did not swallow fire damage");
        helper.succeed();
    }

    /**
     * Power plate: the holder takes the hit unharmed. The resistance is granted inside the hurt
     * and read a few lines later in the same hurt, so this has to be true end to end - asserting
     * the effect instance alone would pass even if the reduction never happened.
     */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void powerPlateCarrierTakesTheHitUnharmed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager carrier = carrierOf(helper, new BlockPos(1, 1, 2), Treasures.POWER_PLATE);
        Villager control = villagerAt(helper, new BlockPos(3, 1, 2));

        TreasureEvents.applyWard(carrier,
                new LivingHurtEvent(carrier, level.damageSources().generic(), 5.0F));

        MobEffectInstance effect = carrier.getEffect(MobEffects.DAMAGE_RESISTANCE);
        S2StorageGameTests.check(helper,
                effect != null && effect.getAmplifier() == 4 && effect.getDuration() == 1,
                "the power plate did not grant a one-tick Resistance V: " + effect);

        carrier.hurt(level.damageSources().generic(), 5.0F);
        control.hurt(level.damageSources().generic(), 5.0F);
        S2StorageGameTests.check(helper, carrier.getHealth() == carrier.getMaxHealth(),
                "Resistance V did not absorb the hit: " + carrier.getHealth());
        S2StorageGameTests.check(helper, control.getHealth() == control.getMaxHealth() - 5.0F,
                "the control villager was shielded too: " + control.getHealth());
        helper.succeed();
    }

    /** Golden wing: the attacker is launched, not the holder. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void goldenWingCarrierLaunchesTheAttacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager carrier = carrierOf(helper, new BlockPos(1, 1, 2), Treasures.GOLDEN_WING);
        Zombie attacker = zombieAt(helper, new BlockPos(3, 1, 2));

        TreasureEvents.applyWard(carrier, meleeFrom(level, carrier, attacker));

        S2StorageGameTests.check(helper, attacker.getDeltaMovement().y > 1.0D,
                "the golden wing did not launch the attacker: " + attacker.getDeltaMovement().y);
        S2StorageGameTests.check(helper, Math.abs(carrier.getDeltaMovement().y) < STILL,
                "the golden wing launched its own holder: " + carrier.getDeltaMovement().y);
        helper.succeed();
    }

    /** The other half of the pair: a sealed carrier stays quiet. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void sealedCarrierDoesNotRetaliate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager carrier = carrierOf(helper, new BlockPos(1, 1, 2), Treasures.GOLDEN_WING);
        carrier.getPersistentData().putBoolean(SEALED, true);
        Zombie attacker = zombieAt(helper, new BlockPos(3, 1, 2));

        TreasureEvents.applyWard(carrier, meleeFrom(level, carrier, attacker));

        S2StorageGameTests.check(helper, Math.abs(attacker.getDeltaMovement().y) < STILL,
                "a sealed carrier retaliated anyway: " + attacker.getDeltaMovement().y);
        helper.succeed();
    }

    /** An unsealed carrier's death destroys the treasure instead of dropping it. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void unsealedCarrierIsDestroyedNotDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        Villager carrier = carrierOf(helper, relative, Treasures.POWER_PLATE);
        BlockPos world = helper.absolutePos(relative);
        BlockPos marker = world.offset(1, 0, 0);
        level.setBlockAndUpdate(marker, Blocks.GLASS.defaultBlockState());

        TreasureEvents.settleDeath(level, carrier);

        // Every breakable block within 5 becomes obsidian - air included, since air reads as
        // destroy speed 0 and only -1 is the unbreakable sentinel. The placed marker proves a
        // real block was converted, not just the empty space.
        S2StorageGameTests.check(helper, level.getBlockState(world).is(Blocks.OBSIDIAN),
                "the power plate did not turn its carrier's block to obsidian: " + level.getBlockState(world));
        S2StorageGameTests.check(helper, level.getBlockState(marker).is(Blocks.OBSIDIAN),
                "a placed block inside the radius survived: " + level.getBlockState(marker));
        S2StorageGameTests.check(helper, !carrier.getPersistentData().contains(RitualExtraction.TREASURE_TAG),
                "the destroyed carrier still holds its treasure tag");
        S2StorageGameTests.check(helper, !droppedTreasureNear(level, world),
                "a destroyed treasure was dropped anyway");
        helper.succeed();
    }

    /** A sealed carrier's death drops the treasure intact, and it stays dormant. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void sealedCarrierDropsItsTreasure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        Villager carrier = carrierOf(helper, relative, Treasures.POWER_PLATE);
        carrier.getPersistentData().putBoolean(SEALED, true);
        BlockPos world = helper.absolutePos(relative);
        BlockPos marker = world.offset(1, 0, 0);
        level.setBlockAndUpdate(marker, Blocks.GLASS.defaultBlockState());

        TreasureEvents.settleDeath(level, carrier);

        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(world).inflate(4.0D));
        S2StorageGameTests.check(helper, drops.size() == 1,
                "a sealed carrier did not drop exactly one item: " + drops.size());
        S2StorageGameTests.check(helper, drops.get(0).getItem().is(TechnomItems.TREASURE_POWER_PLATE.get()),
                "the sealed carrier dropped the wrong item: " + drops.get(0).getItem());
        // The positive form of "it stayed dormant": the block the destroy effect would have
        // overwritten is untouched, and the carrier no longer carries anything.
        S2StorageGameTests.check(helper, level.getBlockState(marker).is(Blocks.GLASS),
                "a sealed carrier detonated anyway: " + level.getBlockState(marker));
        S2StorageGameTests.check(helper, !carrier.getPersistentData().contains(RitualExtraction.TREASURE_TAG),
                "the sealed carrier still holds its treasure tag");
        helper.succeed();
    }

    /** The third death effect, and the only one that touches no blocks: a launch, not a drop. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void goldenWingCarrierDeathLaunchesNearbyEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        Villager carrier = carrierOf(helper, relative, Treasures.GOLDEN_WING);
        Zombie bystander = zombieAt(helper, new BlockPos(3, 1, 2));
        BlockPos world = helper.absolutePos(relative);

        TreasureEvents.settleDeath(level, carrier);

        S2StorageGameTests.check(helper, bystander.getDeltaMovement().y > 1.0D,
                "the golden wing did not launch the bystander: " + bystander.getDeltaMovement().y);
        S2StorageGameTests.check(helper, !droppedTreasureNear(level, world),
                "the launched treasure was dropped anyway");
        helper.succeed();
    }

    /** The name-to-treasure lookup, including the unknown-name case the ritual relies on. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void treasureNamesMapToTheirAffinities(GameTestHelper helper) {
        S2StorageGameTests.check(helper, affinityOf(Treasures.FIRE_GEM) == Affinity.FIRE, "fireGem affinity");
        S2StorageGameTests.check(helper, affinityOf(Treasures.POWER_PLATE) == Affinity.DARK, "powerPlate affinity");
        S2StorageGameTests.check(helper, affinityOf(Treasures.GOLDEN_WING) == Affinity.LIGHT, "goldenWing affinity");
        S2StorageGameTests.check(helper, Treasures.item("nonsense") == null, "an unknown name resolved to an item");
        S2StorageGameTests.check(helper, Treasures.get("nonsense").isEmpty(),
                "an unknown name resolved to a stack");
        helper.succeed();
    }
}
