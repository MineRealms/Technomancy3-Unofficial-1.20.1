package theflogat.technomancy.common.rituals.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** {@code RitualWaterT3}: three water rings, filling a 19x19 column down to the world floor. */
public class RitualWaterT3 extends RitualWater {

    public RitualWaterT3() {
        super(new Type[] {Type.WATER, Type.WATER, Type.WATER}, Type.WATER, 9, 9);
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        removeFrame(level, pos);
        int floor = level.getMinBuildHeight();
        for (int y = pos.getY() - 1; y >= floor; y--) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    placeWater(level, new BlockPos(pos.getX() + dx, y, pos.getZ() + dz));
                }
            }
        }
    }
}
