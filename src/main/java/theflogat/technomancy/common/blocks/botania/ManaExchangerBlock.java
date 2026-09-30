package theflogat.technomancy.common.blocks.botania;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.common.tiles.botania.ManaExchangerBlockEntity;
import theflogat.technomancy.common.wands.TechnomWrench;
import vazkii.botania.api.mana.PoolOverlayProvider;

/**
 * {@code BlockManaExchanger}: a Mana pool must sit on top of it. The original painted a different
 * side texture per mode and an overlay on the pool above when it was working; both are block
 * states here so the client needs no custom packet. The pool overlay is served through Botania's
 * {@link PoolOverlayProvider}, which 1.20.1 looks up on the block below the pool.
 */
public class ManaExchangerBlock extends BaseEntityBlock implements PoolOverlayProvider {

    /**
     * The original's {@code mode}: {@code true} means the pool gains mana and the tank is drained
     * (fluid to mana), {@code false} means the pool is drained and the tank filled (mana to fluid).
     */
    public static final BooleanProperty OUT = BooleanProperty.create("out");
    /** Whether the exchange is enabled right now; drives the overlay on the pool above. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final ResourceLocation TOP_ACTIVE =
            new ResourceLocation(Technomancy.MOD_ID, "block/manaexchangertopactive");
    private static final ResourceLocation TOP_INACTIVE =
            new ResourceLocation(Technomancy.MOD_ID, "block/manaexchangertopinactive");

    public ManaExchangerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OUT, false).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUT, ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** {@code onWrenched}: a wrench flips between importing and exporting mana. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ManaExchangerBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (TechnomWrench.isWrench(held)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            level.setBlock(pos, state.setValue(OUT, !state.getValue(OUT)), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            return InteractionResult.CONSUME;
        }
        return RedstoneControl.interact(machine.redstone(), level, pos, player, held);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaExchangerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, theflogat.technomancy.compat.botania.BotaniaContent.MANA_EXCHANGER_BE.get(),
                        ManaExchangerBlockEntity::serverTick);
    }

    /** Reads the exchanger's own state, since the pool renderer passes the pool's position. */
    @Override
    public ResourceLocation getIcon(Level level, BlockPos poolPos) {
        BlockState below = level.getBlockState(poolPos.below());
        boolean active = below.getBlock() == this && below.getValue(ACTIVE);
        return active ? TOP_ACTIVE : TOP_INACTIVE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines,
            TooltipFlag flag) {
        lines.add(Component.translatable("block.technom.mana_exchanger.mode")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("block.technom.mana_exchanger.wrench")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
