package theflogat.technomancy.common.tiles.technom.existence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * {@code TileExistenceBurner}: kills the living around it and turns each into Existence power.
 * The dynamic variant costs 10,000 energy per kill and higher caps; one block entity serves both,
 * switched by {@link #dynamic()}.
 */
public final class ExistenceBurnerBlockEntity extends BlockEntity implements IExistenceProducer {

    private static final int STATIC_CAP = 100;
    private static final int DYNAMIC_CAP = 150;
    private static final int DYNAMIC_ENERGY_PER_KILL = 10_000;
    private static final String TAG_POWER = "power";

    private int power;
    private final boolean dynamic;

    public ExistenceBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.EXISTENCE_BURNER.get(), pos, state);
        this.dynamic = state.getBlock()
                instanceof theflogat.technomancy.common.blocks.technom.existence.ExistenceBurnerBlock burner
                && burner.dynamic();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExistenceBurnerBlockEntity burner) {
        if (burner.power >= burner.getPowerCap()) {
            return;
        }
        AABB box = new AABB(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof Player || entity.isInvulnerable() || !entity.isAlive()) {
                continue;
            }
            burner.power += value(entity);
            entity.kill();
        }
        if (burner.power > burner.getPowerCap()) {
            burner.power = burner.getPowerCap();
        }
        burner.setChanged();
    }

    private static int value(LivingEntity entity) {
        if (entity instanceof AbstractVillager) {
            return 15;
        }
        return entity instanceof Mob ? 1 : 2;
    }

    public boolean dynamic() {
        return dynamic;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public int getPowerCap() {
        return dynamic ? DYNAMIC_CAP : STATIC_CAP;
    }

    @Override
    public int getMaxRate() {
        return dynamic ? 4 : 4;
    }

    @Override
    public void addPower(int value) {
        power += value;
        setChanged();
    }

    @Override
    public boolean canInput() {
        return false;
    }

    @Override
    public boolean canOutput() {
        return dynamic ? power > 0 : true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_POWER, power);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power = tag.getInt(TAG_POWER);
    }
}
