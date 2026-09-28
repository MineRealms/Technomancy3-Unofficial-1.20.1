package theflogat.technomancy.common.rituals.dark;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.common.machines.existence.ExistenceConversion;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.rituals.IRitualEffectHandler;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;

/**
 * {@code RitualFountainExistence}: consumes its frame and then, each tick, drains the lives
 * around it into Existence; once enough is gathered the catalyst becomes a fountain.
 */
public class RitualFountainExistence extends Ritual implements IRitualEffectHandler {

    /** {@code TileExistenceFountain}: the fountain appears at this much Existence. */
    public static final int THRESHOLD = 1000;

    public RitualFountainExistence() {
        super(new Type[] {Type.LIGHT, Type.DARK, Type.LIGHT}, Type.DARK);
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        removeFrame(level, pos);
        if (level.getBlockEntity(pos) instanceof CatalystBlockEntity catalyst) {
            catalyst.setHandler(this);
            catalyst.setData(new Object[] {0});
        }
    }

    @Override
    public void applyEffect(CatalystBlockEntity catalyst) {
        Level level = catalyst.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = catalyst.getBlockPos();
        int collected = catalyst.data() != null && catalyst.data().length > 0
                ? (Integer) catalyst.data()[0] : 0;
        if (collected >= THRESHOLD) {
            level.setBlock(pos, TechnomBlocks.EXISTENCE_FOUNTAIN.get().defaultBlockState(), 3);
            return;
        }
        AABB box = new AABB(pos.getX() - 11, pos.getY() - 11, pos.getZ() - 11,
                pos.getX() + 12, pos.getY() + 12, pos.getZ() + 12);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            if (entity instanceof Player || entity.isInvulnerable()) {
                continue;
            }
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
                continue;
            }
            collected += ExistenceConversion.getValue(living);
            living.kill();
        }
        catalyst.setData(new Object[] {collected});
    }
}
