package theflogat.technomancy.common.tiles.technom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The crystal's block entity ({@code TileCrystal}), which deliberately carries no state.
 *
 * <p>1.7.10 needed a tile here for one reason only: a {@code TileEntitySpecialRenderer} could not
 * be attached to a plain block, and the crystal had to be drawn in code. Nothing about the crystal
 * is actually per-position state - the kind is the block, and the stage is a neighbour query that
 * both sides can run ({@code CrystalBlock.stage}) - so upstream's tile held nothing either. This
 * class exists to host the renderer, and for no other purpose.</p>
 *
 * <p>Do not add fields. Anything stored here would need syncing, and the level already knows the
 * answer; a client that receives the block states can derive the same drawing.</p>
 */
public final class CrystalBlockEntity extends BlockEntity {

    public CrystalBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.CRYSTAL.get(), pos, state);
    }
}
