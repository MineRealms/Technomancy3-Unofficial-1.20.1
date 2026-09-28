package theflogat.technomancy.common.blocks.technom;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;

/**
 * The ritual core ({@code BlockCatalyst}): sneak-right-click or power it to run the matching
 * ritual.
 *
 * <p>One block per kind, as with the crystals. While a ritual is running the catalyst is
 * unbreakable, which is the original's {@code getBlockHardness} returning -1 for
 * {@code remCount != -1}; that countdown is also what removes a black hole's core after it
 * collapses.</p>
 */
public class CatalystBlock extends BaseEntityBlock {

    private final Ritual.Type type;

    public CatalystBlock(Ritual.Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Ritual.Type type() {
        return type;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatalystBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, TechnomBlockEntities.CATALYST.get(),
                        CatalystBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof CatalystBlockEntity catalyst) {
            catalyst.setOwner(player);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.isShiftKeyDown()
                || !(level.getBlockEntity(pos) instanceof CatalystBlockEntity catalyst)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            catalyst.activate(player instanceof net.minecraft.server.level.ServerPlayer server ? server : null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
