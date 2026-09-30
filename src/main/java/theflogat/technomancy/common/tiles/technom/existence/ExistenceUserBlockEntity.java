package theflogat.technomancy.common.tiles.technom.existence;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.common.items.technom.Treasures;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.rituals.earth.RitualExtraction;
import theflogat.technomancy.common.tiles.base.RedstoneControl;

/**
 * The three Existence users ({@code TileExistenceCropAccelerator}, {@code TileExistenceHarvester}
 * and {@code TileExistenceSealingDevice}) as one block entity with a mode.
 *
 * <p>Each spends Existence power: the crop accelerator bonemeals at 30 a stage, the harvester
 * collects a grown crop and replants it at 30, and the sealer spends 500,000 to mark a treasure
 * villager so the Extraction ritual can find it.</p>
 */
public final class ExistenceUserBlockEntity extends BlockEntity implements IExistenceConsumer {

    public enum Mode { CROP, HARVEST, SEAL }

    /** Crop and harvest users hold 10,000; the sealer holds 1,000,000. */
    private static final int SMALL_CAP = 10_000;
    private static final int SEAL_CAP = 1_000_000;
    private static final int CROP_COST = 30;
    private static final int HARVEST_COST = 30;
    private static final int SEAL_COST = 500_000;
    /**
     * The crop accelerator only runs with this much banked, as the original's {@code power>605}:
     * the gate below returns while {@code power <= CROP_FLOOR}, so 605 is the largest amount that
     * does nothing and 606 the smallest that works.
     */
    private static final int CROP_FLOOR = 605;
    private static final String TAG_POWER = "power";
    /** {@code TileExistenceCropAccelerator} / {@code TileExistenceHarvester}: {@code RedstoneSet.LOW}. */
    private static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.LOW;

    private int power;
    private final Mode mode;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);

    public ExistenceUserBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.EXISTENCE_USER.get(), pos, state);
        this.mode = state.getBlock()
                instanceof theflogat.technomancy.common.blocks.technom.existence.ExistenceUserBlock user
                ? user.mode() : Mode.CROP;
        redstone.setListener(this::setChanged);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExistenceUserBlockEntity user) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // The crop accelerator and the harvester check set.canRun upstream; the sealing device
        // (TileExistenceSealingDevice) does not, so the sealer keeps running under any signal.
        if (user.mode != Mode.SEAL && !user.redstone.canRun(level, pos)) {
            return;
        }
        switch (user.mode) {
            case CROP -> user.accelerate(server, pos);
            case HARVEST -> user.harvest(server, pos);
            case SEAL -> user.seal(server, pos);
        }
    }

    private void accelerate(ServerLevel level, BlockPos pos) {
        if (power <= CROP_FLOOR) {
            return;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos cropPos = pos.offset(dx, 2, dz);
                BlockState crop = level.getBlockState(cropPos);
                if (crop.getBlock() instanceof BonemealableBlock growable
                        && growable.isValidBonemealTarget(level, cropPos, crop, false)
                        && power >= CROP_COST) {
                    if (growable.isBonemealSuccess(level, level.random, cropPos, crop)) {
                        growable.performBonemeal(level, level.random, cropPos, crop);
                    }
                    power -= CROP_COST;
                }
            }
        }
        setChanged();
    }

    private void harvest(ServerLevel level, BlockPos pos) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos cropPos = pos.offset(dx, 0, dz);
                BlockState crop = level.getBlockState(cropPos);
                if (!(crop.getBlock() instanceof BonemealableBlock growable)
                        || growable.isValidBonemealTarget(level, cropPos, crop, false)) {
                    continue;
                }
                if (power < HARVEST_COST) {
                    return;
                }
                List<ItemStack> drops = Block.getDrops(crop, level, cropPos, null);
                level.setBlock(cropPos, crop.getBlock().defaultBlockState(), 3);
                // TileExistenceHarvester takes one item back out of the drops to pay for the
                // replant: whichever drop is the block's own item form (the wheat, the carrot),
                // because that is what it puts back in the ground. Replanting for free handed the
                // player one extra crop every harvest.
                Item replant = crop.getBlock().asItem();
                for (ItemStack drop : drops) {
                    if (drop.is(replant)) {
                        drop.shrink(1);
                    }
                    if (drop.isEmpty()) {
                        continue;
                    }
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0,
                            pos.getZ() + 0.5, drop));
                }
                power -= HARVEST_COST;
            }
        }
        setChanged();
    }

    private void seal(ServerLevel level, BlockPos pos) {
        if (power < SEAL_COST) {
            return;
        }
        AABB box = new AABB(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
            CompoundTag data = villager.getPersistentData();
            if (data.contains(RitualExtraction.TREASURE_TAG) && !data.getBoolean("seal")) {
                data.putBoolean("seal", true);
                power -= SEAL_COST;
                setChanged();
                return;
            }
        }
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public int getPowerCap() {
        return mode == Mode.SEAL ? SEAL_CAP : SMALL_CAP;
    }

    /**
     * {@code TileExistenceRedstoneBase.getMaxRate()}: {@code maxPower / 50}, which is 200 for the
     * crop accelerator and the harvester and 20,000 for the sealer. The pylon uses this as its
     * per-tick injection cap, so a flat 4 filled the whole network 50 to 5,000 times too slowly.
     */
    @Override
    public int getMaxRate() {
        return getPowerCap() / 50;
    }

    @Override
    public void addPower(int value) {
        power = Math.max(0, Math.min(getPowerCap(), power + value));
        setChanged();
    }

    @Override
    public boolean canInput() {
        return true;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_POWER, power);
        redstone.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power = tag.getInt(TAG_POWER);
        redstone.load(tag);
    }
}
