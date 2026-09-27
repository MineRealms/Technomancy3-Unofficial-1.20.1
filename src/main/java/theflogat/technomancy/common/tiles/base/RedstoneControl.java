package theflogat.technomancy.common.tiles.base;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Redstone mode of one machine block entity, plus the right-click interaction that changes it.
 *
 * <p>The {@code modified} flag exists so the programming item is only ever handed back if the
 * player put one in: a machine still on its factory default owes nothing.</p>
 */
public final class RedstoneControl {

    private static final String TAG_MODE = "redstone_mode";
    private static final String TAG_MODIFIED = "redstone_modified";

    private final RedstoneMode defaultMode;
    private RedstoneMode mode;
    private boolean modified;
    private Runnable listener = () -> {};

    public RedstoneControl(RedstoneMode defaultMode) {
        this.defaultMode = Objects.requireNonNull(defaultMode, "defaultMode");
        this.mode = defaultMode;
    }

    public void setListener(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public RedstoneMode mode() {
        return mode;
    }

    /** Whether a player ever set this mode, and therefore whether an item is owed back. */
    public boolean isModified() {
        return modified;
    }

    public boolean canRun(Level level, BlockPos pos) {
        return mode.canRun(level, pos);
    }

    /**
     * Sets the mode as a player action, so the previous programming item becomes refundable.
     *
     * @return {@code true} if the mode actually changed
     */
    public boolean set(RedstoneMode selected) {
        if (mode == selected) {
            return false;
        }
        mode = selected;
        modified = true;
        listener.run();
        return true;
    }

    /**
     * Handles a right-click with one of the three programming items.
     *
     * <p>All state changes happen on the server only; the client just reports success so the
     * arm swings. The original mutated both sides and produced its feedback client-side, which
     * is the same class of bug as defect A-3 on the jar.</p>
     *
     * @return {@link InteractionResult#PASS} when the held item is not a programming item or
     *         already selects the current mode, so the caller can go on to its other branches
     */
    public static InteractionResult interact(RedstoneControl control, Level level, BlockPos pos,
            Player player, ItemStack held) {
        RedstoneMode selected = RedstoneMode.forItem(held.getItem()).orElse(null);
        if (selected == null || selected == control.mode()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The refund has to be read before the mode changes, and it has to happen before the
        // new item is consumed so a full inventory drops the old item rather than eating it.
        ItemStack refund = control.isModified()
                ? new ItemStack(control.mode().programmingItem())
                : ItemStack.EMPTY;
        control.set(selected);
        if (!refund.isEmpty() && !player.getInventory().add(refund)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, refund);
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 1.0F);
        // These machines have no GUI, so the mode is otherwise invisible. The original gave no
        // feedback at all, which is half the reason it read as broken.
        player.displayClientMessage(
                Component.translatable("technom.redstone_mode." + selected.getSerializedName()), true);
        return InteractionResult.CONSUME;
    }

    /** The item a broken machine should drop, or empty if the mode was never changed. */
    public ItemStack refund() {
        return modified ? new ItemStack(mode.programmingItem()) : ItemStack.EMPTY;
    }

    public void save(CompoundTag tag) {
        tag.putString(TAG_MODE, mode.getSerializedName());
        if (modified) {
            tag.putBoolean(TAG_MODIFIED, true);
        }
    }

    public void load(@Nullable CompoundTag tag) {
        if (tag == null) {
            mode = defaultMode;
            modified = false;
            return;
        }
        mode = RedstoneMode.byName(tag.contains(TAG_MODE) ? tag.getString(TAG_MODE) : null, defaultMode);
        modified = tag.getBoolean(TAG_MODIFIED);
    }
}
