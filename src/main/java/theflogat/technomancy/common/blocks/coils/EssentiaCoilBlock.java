package theflogat.technomancy.common.blocks.coils;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.coils.CoilBlockEntity;
import theflogat.technomancy.common.tiles.coils.EssentiaCoilBlockEntity;

/**
 * {@code teslaCoil}, registered upstream as {@code TMBlocks.teslaCoil} but implemented by
 * {@code BlockEssentiaTransmitter}. Filtered with a printed Thaumcraft jar label, which is handed
 * back intact when the filter is removed or the block is broken, exactly as the original did.
 */
public class EssentiaCoilBlock extends CoilBlock {

    /** Thaumcraft's jar label; the original used {@code itemResource:13}. */
    private static final ResourceLocation LABEL_ITEM = new ResourceLocation("thaumcraft", "jar_label");

    public EssentiaCoilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaCoilBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends CoilBlockEntity> type() {
        return TechnomBlockEntities.ESSENTIA_COIL.get();
    }

    @Override
    protected InteractionResult useFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player,
            ItemStack held) {
        if (!(coil instanceof EssentiaCoilBlockEntity essentia) || essentia.filter() != null
                || !EssentiaContainerApi.isLabel(held)) {
            return InteractionResult.PASS;
        }
        AspectId printed = EssentiaContainerApi.labelAspect(held).orElse(null);
        if (printed == null) {
            // A blank label carries no aspect; the original silently did nothing here.
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            essentia.setFilter(printed);
            if (!player.isCreative()) {
                held.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.technom.coil.filter_set",
                    AspectApi.tooltipName(printed)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult removeFilter(CoilBlockEntity coil, Level level, BlockPos pos, Player player) {
        if (!(coil instanceof EssentiaCoilBlockEntity essentia) || essentia.filter() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack label = printedLabel(essentia.filter());
            essentia.setFilter(null);
            if (!label.isEmpty()) {
                give(level, pos, player, label);
            }
            player.displayClientMessage(Component.translatable("message.technom.coil.filter_cleared"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    protected Component filterName(CoilBlockEntity coil) {
        return coil instanceof EssentiaCoilBlockEntity essentia && essentia.filter() != null
                ? AspectApi.tooltipName(essentia.filter())
                : null;
    }

    @Override
    protected ItemStack filterRefund(CoilBlockEntity coil) {
        return coil instanceof EssentiaCoilBlockEntity essentia && essentia.filter() != null
                ? printedLabel(essentia.filter())
                : ItemStack.EMPTY;
    }

    /** The same printed label the player put in, or nothing if Thaumcraft has no label item. */
    private static ItemStack printedLabel(@Nullable AspectId aspect) {
        Item label = BuiltInRegistries.ITEM.get(LABEL_ITEM);
        if (label == Items.AIR || aspect == null) {
            return ItemStack.EMPTY;
        }
        ItemStack blank = new ItemStack(label);
        try {
            return EssentiaContainerApi.withFilter(blank, aspect);
        } catch (IllegalArgumentException unknownAspect) {
            // The saved aspect is gone from the registry; hand back a blank label rather than
            // swallowing the item.
            return blank;
        }
    }

    /**
     * For the wrench: any essentia endpoint that will connect to the coil, whether it accepts
     * pushed essentia or fetches its own. The original looked for a TC4 {@code IAspectContainer}.
     */
    @Override
    protected boolean canFeed(Level level, BlockPos target, Direction facing) {
        BlockPos coil = target.relative(facing.getOpposite());
        return level.isLoaded(target)
                && ThaumcraftApiHelper.getConnectableTransport(level, coil, facing) != null;
    }
}
