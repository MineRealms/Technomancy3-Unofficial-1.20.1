package theflogat.technomancy.common.rituals.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import theflogat.technomancy.common.rituals.Ritual;

/**
 * The fire family ({@code RitualOfFire}): consumes its frame and works on a 19x19x19 column
 * below the core. Tier one sets water to obsidian, tier two sets air to lava.
 */
public class RitualOfFire extends Ritual {

    /** T1 hardens water, T2 floods air with lava. */
    private final boolean obsidian;

    public RitualOfFire(Type[] frame, boolean obsidian) {
        super(frame, Type.FIRE);
        this.obsidian = obsidian;
    }

    @Override
    public boolean canApplyEffect(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public void applyEffect(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        removeFrame(level, pos);
        int depth = level.getMinBuildHeight();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int y = pos.getY() - 1; y >= Math.max(depth, pos.getY() - 19); y--) {
                    BlockPos target = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (obsidian) {
                        if (level.getFluidState(target).is(net.minecraft.tags.FluidTags.WATER)) {
                            level.setBlock(target, Blocks.OBSIDIAN.defaultBlockState(), 3);
                        }
                    } else if (level.getBlockState(target).isAir()) {
                        level.setBlock(target, Blocks.LAVA.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
