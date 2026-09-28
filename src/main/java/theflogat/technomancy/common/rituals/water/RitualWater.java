package theflogat.technomancy.common.rituals.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * The water family ({@code RitualWater}): consumes its frame and floods the layer under the
 * core. Tier three instead floods the whole column down to the world floor.
 */
public class RitualWater extends Ritual {

    protected final int radiusX;
    protected final int radiusZ;

    public RitualWater(Type[] frame, Type core, int radiusX, int radiusZ) {
        super(frame, core);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        removeFrame(level, pos);
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                placeWater(level, pos.offset(dx, -1, dz));
            }
        }
    }

    protected static void placeWater(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        }
    }
}
