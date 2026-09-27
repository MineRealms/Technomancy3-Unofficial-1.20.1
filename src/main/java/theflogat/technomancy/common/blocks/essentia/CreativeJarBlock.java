package theflogat.technomancy.common.blocks.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;

/**
 * {@code BlockCreativeJar}. Creative-only by construction: it has no recipe, it is unbreakable
 * outside creative mode (so neither a survival player nor a block-eating machine can harvest
 * it) and its loot table drops nothing.
 *
 * <p>Interactions, all resolved on the server:</p>
 * <ul>
 *   <li>a filled phial or jar item, or a jar label, sets the aspect — creative players only,
 *       and the item is left as it was (the original consumed the phial's eight units into a
 *       jar that was infinite anyway);</li>
 *   <li>an empty phial or jar item is filled for free, one item at a time, by anyone;</li>
 *   <li>sneaking with an empty hand clears the aspect — creative players only.</li>
 * </ul>
 */
public class CreativeJarBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public CreativeJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeJarBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CreativeJarBlockEntity jar)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        if (held.isEmpty()) {
            if (!player.isSecondaryUseActive() || !creative || jar.aspect() == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                jar.setAspect(null);
                level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.4F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        AspectId offered = offeredAspect(held);
        if (offered != null) {
            if (!creative) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && jar.setAspect(offered)) {
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return fillCarrier(level, pos, player, hand, jar, held);
    }

    /** The aspect a filled carrier holds or a label names, else {@code null}. */
    @Nullable
    private static AspectId offeredAspect(ItemStack held) {
        if (EssentiaContainerApi.isLabel(held)) {
            return EssentiaContainerApi.labelAspect(held).orElse(null);
        }
        if (EssentiaContainerApi.capacity(held) > 0) {
            return EssentiaContainerApi.contents(held).amounts().keySet().stream().findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Fills one empty carrier. {@code EssentiaContainerApi} only transacts on a single item, so
     * one is split off a stack, filled, and handed over; the jar is infinite, so nothing is
     * debited and nothing can be duplicated beyond what a creative source already grants.
     */
    private static InteractionResult fillCarrier(Level level, BlockPos pos, Player player,
            InteractionHand hand, CreativeJarBlockEntity jar, ItemStack held) {
        AspectId aspect = jar.aspect();
        int capacity = EssentiaContainerApi.capacity(held);
        if (aspect == null || capacity <= 0) {
            return InteractionResult.PASS;
        }
        ItemStack single = held.copyWithCount(1);
        if (EssentiaContainerApi.insert(level, single, aspect, capacity, EssentiaTransferMode.SIMULATE) <= 0) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (EssentiaContainerApi.insert(level, single, aspect, capacity, EssentiaTransferMode.EXECUTE) <= 0) {
            return InteractionResult.PASS;
        }
        if (held.getCount() == 1) {
            player.setItemInHand(hand, single);
        } else {
            held.shrink(1);
            if (!player.getInventory().add(single)) {
                player.drop(single, false);
            }
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4F, 1.0F);
        return InteractionResult.CONSUME;
    }
}
