package theflogat.technomancy.common.rituals.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * The cave-in family ({@code RitualCaveIn}): every loose block in the column area drops to the
 * lowest spot it can occupy, filling the gaps under it.
 *
 * <p>Deliberate change: the original moved "movable" block entities down with their block, which
 * needed a per-block registry and could still strand or duplicate an inventory. Here the ritual
 * refuses to run at all if any block entity stands in the volume, so chests are never crushed;
 * everything else falls.</p>
 */
public class RitualCaveIn extends Ritual {

    protected final int radiusX;
    protected final int radiusZ;

    public RitualCaveIn(Type[] frame, Type core, int radiusX, int radiusZ) {
        super(frame, core);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        for (int y = 1; y < pos.getY(); y++) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    if (!level.getBlockState(pos.offset(dx, y - pos.getY(), dz)).isAir()
                            && level.getBlockEntity(pos.offset(dx, y - pos.getY(), dz)) != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        removeFrame(level, pos);

        int floor = level.getMinBuildHeight();
        for (int y = floor + 1; y < pos.getY(); y++) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    BlockPos from = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    BlockState state = level.getBlockState(from);
                    if (state.isAir()) {
                        continue;
                    }
                    for (int target = floor; target < y; target++) {
                        BlockPos to = new BlockPos(from.getX(), target, from.getZ());
                        BlockState below = level.getBlockState(to);
                        if (below.isAir() || below.canBeReplaced()) {
                            level.setBlock(to, state, 3);
                            level.removeBlock(from, false);
                            break;
                        }
                    }
                }
            }
        }
    }
}
