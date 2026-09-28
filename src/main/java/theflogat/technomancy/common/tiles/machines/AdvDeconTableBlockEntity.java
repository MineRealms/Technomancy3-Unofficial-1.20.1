package theflogat.technomancy.common.tiles.machines;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectPoolApi;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.machines.decon.DeconstructionTable;
import theflogat.technomancy.common.machines.processing.SidedSlotView;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The advanced deconstruction table ({@code TileAdvDeconTable}): slowly destroys one object in
 * its single slot and, rarely, credits a primal research point to the player who placed it.
 *
 * <p>The 1.7.10 machine awarded the point every 20 ticks to the placing player's global pool.
 * That pool no longer exists; TC4R keys it to a player identity instead, so the owner is stored
 * as a UUID and the point is credited through {@link AspectPoolApi} while the owner is online.
 * An offline owner's pending point is held, not lost, and a point the cap would refuse is
 * dropped rather than left set — the original's {@code aspect} field also gated {@code canBreak},
 * so hitting the cap silently jammed the table.</p>
 *
 * <p>Automation sees a real single-slot {@link IItemHandler} (insert and extract), not the empty
 * slot list the 1.12 lines shipped.</p>
 */
public final class AdvDeconTableBlockEntity extends BlockEntity {

    public static final int SLOT = 0;
    public static final int SLOTS = 1;

    private static final String TAG_VERSION = "v";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_PENDING = "Pending";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_BOOST = "Boost";
    private static final int SCHEMA_VERSION = 1;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // The filter is the machine's own rule, so a pipe cannot park an object the table
            // would never be able to break.
            return slot == SLOT && decomposable(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> internalView = LazyOptional.of(() -> items);
    private final LazyOptional<IItemHandler> faceView =
            LazyOptional.of(() -> new SidedSlotView(items, SLOT, SLOT));

    @Nullable
    private UUID owner;
    private String ownerName = "";
    @Nullable
    private AspectId pending;
    private int breaktime;
    private boolean boost;

    public AdvDeconTableBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ADV_DECON_TABLE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvDeconTableBlockEntity table) {
        table.awardPending(level);
        table.work();
    }

    // ---- work loop ----

    private void work() {
        boolean canBreak = canBreak();
        if (breaktime == 0 && canBreak) {
            breaktime = DeconstructionTable.breakTicks(boost);
            setChanged();
        } else if (breaktime > 0 && canBreak) {
            breaktime--;
            if (breaktime == 0) {
                breakItem();
            }
            setChanged();
        } else if (breaktime != 0) {
            breaktime = 0;
            setChanged();
        }
    }

    /** Slot has an object with aspects and no reward is queued. */
    private boolean canBreak() {
        ItemStack stack = items.getStackInSlot(SLOT);
        return !stack.isEmpty() && pending == null && decomposable(stack);
    }

    private void breakItem() {
        ItemStack stack = items.getStackInSlot(SLOT);
        if (stack.isEmpty()) {
            return;
        }
        var primals = DeconstructionTable.reduceToPrimals(AspectQueryApi.item(stack));
        DeconstructionTable.rollReward(primals, level.getRandom()).ifPresent(aspect -> pending = aspect);
        items.extractItem(SLOT, 1, false);
    }

    /**
     * Credits at most one pending point every {@link DeconstructionTable#AWARD_INTERVAL} ticks.
     *
     * <p>The owner is looked up by UUID, so a renamed owner is still the owner. If the owner is
     * offline the point waits; if their pool is already at the cap it is discarded so the table
     * can go on to the next object.</p>
     */
    private void awardPending(Level level) {
        if (pending == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // A table placed without a player (structure, command) has nobody to credit. The point
        // is discarded rather than kept, because a kept point also blocks canBreak forever.
        if (owner == null) {
            pending = null;
            setChanged();
            return;
        }
        if (level.getGameTime() % DeconstructionTable.AWARD_INTERVAL != 0) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        if (!AspectApi.contains(pending)) {
            pending = null;
            setChanged();
            return;
        }
        int current = ThaumcraftApiHelper.getAspectPools(player).amount(pending);
        if (current >= DeconstructionTable.REWARD_CAP) {
            pending = null;
            setChanged();
            return;
        }
        if (AspectPoolApi.add(player, pending, 1, VisAction.EXECUTE) > 0) {
            pending = null;
            setChanged();
        }
    }

    /** Whether an object has any effective aspects, i.e. anything to deconstruct. */
    public static boolean decomposable(ItemStack stack) {
        return !stack.isEmpty() && !AspectQueryApi.item(stack).amounts().isEmpty();
    }

    // ---- owner, upgrade, contents ----

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isBoosted() {
        return boost;
    }

    /** @return {@code true} if the state changed */
    public boolean setBoosted(boolean installed) {
        if (boost == installed) {
            return false;
        }
        boost = installed;
        setChanged();
        return true;
    }

    public int progress() {
        return breaktime;
    }

    @Nullable
    public AspectId pending() {
        return pending;
    }

    public ItemStackHandler items() {
        return items;
    }

    /** Drops the inventory when the machine is broken; the loot table drops only the block. */
    public void dropContents(Level level, BlockPos pos) {
        ItemStack stack = items.getStackInSlot(SLOT);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            items.setStackInSlot(SLOT, ItemStack.EMPTY);
        }
    }

    // ---- capabilities ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? internalView : faceView).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        internalView.invalidate();
        faceView.invalidate();
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
            return;
        }
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.getString(TAG_OWNER_NAME);
        pending = tag.contains(TAG_PENDING, Tag.TAG_STRING)
                ? parseAspect(tag.getString(TAG_PENDING))
                : null;
        breaktime = tag.getInt(TAG_PROGRESS);
        boost = tag.getBoolean(TAG_BOOST);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.put(TAG_ITEMS, items.serializeNBT());
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        if (!ownerName.isEmpty()) {
            tag.putString(TAG_OWNER_NAME, ownerName);
        }
        if (pending != null) {
            tag.putString(TAG_PENDING, pending.serialized());
        }
        tag.putInt(TAG_PROGRESS, breaktime);
        tag.putBoolean(TAG_BOOST, boost);
    }

    @Nullable
    private static AspectId parseAspect(String serialized) {
        try {
            return AspectId.parse(serialized);
        } catch (IllegalArgumentException malformed) {
            Technomancy.LOGGER.warn("Discarding malformed pending aspect '{}'", serialized);
            return null;
        }
    }
}
