package theflogat.technomancy.common.items.coils;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.coils.CoilLink;
import theflogat.technomancy.common.coils.CoilLinks;
import theflogat.technomancy.common.coils.Couplable;

/**
 * The coil coupler: right-click a coil to start linking from it, then right-click each block it
 * should draw from. Clicking a linked block again unlinks it; sneak-clicking a coil clears all of
 * its links; sneak-using the coupler in the air forgets the coil it was linking from.
 *
 * <p>Ported from {@code ItemCoilCoupler}. Differences from the original, all deliberate:</p>
 * <ul>
 *   <li>It acts in {@link #onItemUseFirst}, before the clicked block's own right-click, so an
 *       inventory can be linked without its GUI opening. The original ran after the block, so a
 *       chest could only be linked while sneaking.</li>
 *   <li>The pending coil is stored as a dimension plus position in the stack, never as a block
 *       entity, and is re-resolved and re-validated on every click.</li>
 *   <li>Links are validated for range, duplicates, self links and the link cap, and the coil
 *       itself decides which blocks are acceptable ({@link Couplable#acceptsLinkTarget}) instead
 *       of a class-name blacklist. The original silently ignored a click on the wrong kind of
 *       block and could link the same source any number of times.</li>
 *   <li>Every outcome is reported; the original printed fixed English chat lines.</li>
 * </ul>
 */
public class CoilCouplerItem extends Item {

    private static final String TAG_BOUND = "BoundCoil";
    private static final String TAG_DIMENSION = "dimension";

    public CoilCouplerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockEntity(pos) instanceof Couplable coil) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player != null && player.isSecondaryUseActive()) {
                int removed = coil.links().clear();
                coil.linksChanged();
                tell(player, "cleared", removed);
            } else {
                bind(stack, GlobalPos.of(level.dimension(), pos));
                tell(player, "bound", coil.links().size(), CoilLinks.MAX_LINKS);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.2F);
            return InteractionResult.CONSUME;
        }

        Optional<GlobalPos> bound = boundCoil(stack);
        if (bound.isEmpty()) {
            // Not linking: let the block behave normally.
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        link(stack, level, bound.get(), pos, context.getClickedFace(), player);
        return InteractionResult.CONSUME;
    }

    private static void link(ItemStack stack, Level level, GlobalPos bound, BlockPos target,
            Direction face, @Nullable Player player) {
        if (!bound.dimension().equals(level.dimension())) {
            tell(player, "other_dimension");
            return;
        }
        BlockPos coilPos = bound.pos();
        // isLoaded first: getBlockEntity on an unloaded position would load its chunk.
        if (!level.isLoaded(coilPos) || !(level.getBlockEntity(coilPos) instanceof Couplable coil)) {
            unbind(stack);
            tell(player, "gone");
            return;
        }
        CoilLinks links = coil.links();
        if (links.remove(target)) {
            coil.linksChanged();
            tell(player, "unlinked", links.size(), CoilLinks.MAX_LINKS);
            return;
        }
        CoilLinks.Result check = links.check(coilPos, target);
        switch (check) {
            case SELF -> tell(player, "self");
            case OUT_OF_RANGE -> tell(player, "out_of_range", CoilLinks.MAX_RANGE);
            case FULL -> tell(player, "full", CoilLinks.MAX_LINKS);
            // Unreachable: contains() was answered by the remove() above.
            case DUPLICATE -> tell(player, "unlinked", links.size(), CoilLinks.MAX_LINKS);
            case ADDED -> {
                if (!coil.acceptsLinkTarget(level, target, face)) {
                    tell(player, "wrong_target." + coil.coupleType().id());
                    return;
                }
                links.add(coilPos, new CoilLink(target, face));
                coil.linksChanged();
                tell(player, "linked", links.size(), CoilLinks.MAX_LINKS);
                level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F);
            }
        }
    }

    /** Sneak-use in the air forgets the pending coil. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || boundCoil(stack).isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            unbind(stack);
            tell(player, "unbound");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundCoil(stack).isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Optional<GlobalPos> bound = boundCoil(stack);
        if (bound.isPresent()) {
            BlockPos pos = bound.get().pos();
            lines.add(Component.translatable("item.technom.coil_coupler.bound", pos.getX(), pos.getY(), pos.getZ(),
                    bound.get().dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("item.technom.coil_coupler.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- stack state ----

    public static void bind(ItemStack stack, GlobalPos coil) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, coil.dimension().location().toString());
        tag.putInt("x", coil.pos().getX());
        tag.putInt("y", coil.pos().getY());
        tag.putInt("z", coil.pos().getZ());
        stack.getOrCreateTag().put(TAG_BOUND, tag);
    }

    public static void unbind(ItemStack stack) {
        stack.removeTagKey(TAG_BOUND);
    }

    public static Optional<GlobalPos> boundCoil(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_BOUND, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(TAG_BOUND);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimension == null) {
            return Optional.empty();
        }
        return Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))));
    }

    private static void tell(@Nullable Player player, String key, Object... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.technom.coil." + key, args), true);
        }
    }
}
