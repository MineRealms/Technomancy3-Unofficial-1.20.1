package theflogat.technomancy.common.rituals.light;

import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import theflogat.technomancy.common.rituals.IRitualEffectHandler;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;

/**
 * The purification family ({@code RitualPurification}): while it runs, hostile mobs inside the
 * box are removed every tick. The tier only changes the box.
 */
public class RitualPurification extends Ritual implements IRitualEffectHandler {

    private final int radiusX;
    private final int radiusZ;
    private final int minY;
    private final int maxY;

    public RitualPurification(Type[] frame, Type core, int radiusX, int radiusZ, int minY, int maxY) {
        super(frame, core);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.minY = minY;
        this.maxY = maxY;
    }

    private AABB box(net.minecraft.core.BlockPos pos) {
        return new AABB(
                pos.getX() - radiusX, pos.getY() + minY, pos.getZ() - radiusZ,
                pos.getX() + radiusX + 1.0, pos.getY() + maxY + 1.0, pos.getZ() + radiusZ + 1.0);
    }

    @Override
    public void applyEffect(CatalystBlockEntity catalyst) {
        Level level = catalyst.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        for (Monster monster : level.getEntitiesOfClass(Monster.class, box(catalyst.getBlockPos()))) {
            if (!monster.isInvulnerable()) {
                monster.kill();
            }
        }
    }

    @Override
    public boolean canApplyEffect(Level level, net.minecraft.core.BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CatalystBlockEntity catalyst) {
            catalyst.setHandler(this);
        }
    }
}
