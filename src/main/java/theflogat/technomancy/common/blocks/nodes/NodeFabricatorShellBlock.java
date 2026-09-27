package theflogat.technomancy.common.blocks.nodes;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.nodes.NodeFabricatorShellBlockEntity;

/**
 * The invisible, unbreakable port block that fills the rest of a node fabricator's slab.
 *
 * <p>{@code BlockFakeAirNG} was a {@code Material.carpet} block with {@code setBlockUnbreakable}
 * and the {@code fakeair} icon, so it had no collision and could not be mined; it kept the
 * original's own {@code getUnlocalizedName} override pointing at the fabricator's name. Here it
 * draws nothing at all, because the fabricator's own model covers the slab, and it has no
 * {@code BlockItem} and no loot table: the only way to get one is for a fabricator to place it.</p>
 */
public class NodeFabricatorShellBlock extends BaseEntityBlock {

    public NodeFabricatorShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeFabricatorShellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.NODE_FABRICATOR_SHELL.get(),
                        NodeFabricatorShellBlockEntity::serverTick);
    }

    /**
     * Breaking a shell in creative mode takes the whole fabricator down with it, rather than
     * leaving a machine with a hole in its slab that silently stops working.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NodeFabricatorShellBlockEntity shell) {
            BlockPos host = shell.host();
            if (host != null && level.isLoaded(host)) {
                level.destroyBlock(host, true, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Endermen and the like cannot take a shell either; the blast resistance that stops
     * explosions comes from the block properties ({@code strength(-1, 3600000)}).
     */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos,
            net.minecraft.world.entity.Entity entity) {
        return false;
    }
}
