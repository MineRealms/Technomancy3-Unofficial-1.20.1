package theflogat.technomancy.common.rituals.dark;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.common.rituals.IRitualEffectHandler;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;

/**
 * The black-hole family ({@code RitualBlackHole}): clears a box of everything breakable, then
 * leaves the consumed core behind for a short countdown before it removes itself.
 *
 * <p>The original's per-tick handler killed entities that were <em>invulnerable</em>
 * ({@code if (ent.isEntityInvulnerable())}), which is backwards and would have ignored ordinary
 * mobs while deleting protected ones. This version removes non-invulnerable mobs, which is what
 * a black hole is supposed to do, and never touches players.</p>
 */
public abstract class RitualBlackHole extends Ritual implements IRitualEffectHandler {

    /** {@code TileCatalyst.remCount = 60}: the core lingers for three seconds. */
    public static final int COLLAPSE_TICKS = 60;

    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;

    public RitualBlackHole(Type[] frame, Type core, int radiusX, int radiusY, int radiusZ) {
        super(frame, core);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
    }

    private AABB box(BlockPos pos) {
        return new AABB(pos.getX() - radiusX, pos.getY() - radiusY, pos.getZ() - radiusZ,
                pos.getX() + radiusX + 1.0, pos.getY() + radiusY + 1.0, pos.getZ() + radiusZ + 1.0);
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(CatalystBlockEntity catalyst) {
        Level level = catalyst.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box(catalyst.getBlockPos()))) {
            if (!mob.isInvulnerable()) {
                mob.kill();
            }
        }
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    BlockPos target = pos.offset(dx, dy, dz);
                    if (target.equals(pos) || level.getBlockState(target).getDestroySpeed(level, target) < 0) {
                        continue;
                    }
                    level.removeBlock(target, false);
                }
            }
        }
        removeFrame(level, pos);
        if (level.getBlockEntity(pos) instanceof CatalystBlockEntity catalyst) {
            catalyst.setHandler(this);
            catalyst.setRemCount(COLLAPSE_TICKS);
        }
    }
}
