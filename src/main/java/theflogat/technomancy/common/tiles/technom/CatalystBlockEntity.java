package theflogat.technomancy.common.tiles.technom;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.player.PlayerAffinity;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.rituals.IRitualEffectHandler;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.rituals.RitualFx;
import theflogat.technomancy.common.rituals.RitualRegistry;

/**
 * The catalyst's state ({@code TileCatalyst}): which ritual is running, how long until the core
 * is consumed, and who to credit.
 *
 * <p>The original activated on every tick the block was powered, so a lever left on re-ran the
 * ritual continuously. Here activation happens on the rising edge only. The owner is a UUID
 * rather than a display name, so the credit survives a rename, and an offline owner simply gets
 * no affinity instead of a null dereference.</p>
 */
public final class CatalystBlockEntity extends BlockEntity {

    private static final String TAG_REM_COUNT = "remcount";
    private static final String TAG_RUNNING = "running";
    private static final String TAG_OWNER = "owner";

    /** {@code -1} means no countdown; Purification and Water leave it there. */
    private int remCount = -1;
    /** Index into {@link RitualRegistry}, or {@code -1} while idle. */
    private int handlerId = -1;
    @Nullable
    private UUID owner;
    /** Scratch state a running ritual keeps, e.g. the fountain's collected existence. */
    @Nullable
    private Object[] data;
    private boolean powered;

    public CatalystBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.CATALYST.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CatalystBlockEntity catalyst) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal && !catalyst.powered) {
            catalyst.activate(catalyst.onlineOwner(level));
        }
        catalyst.powered = signal;

        Ritual running = catalyst.running();
        if (running instanceof IRitualEffectHandler handler) {
            handler.applyEffect(catalyst);
        }
        if (catalyst.remCount != -1) {
            if (catalyst.remCount == 0) {
                level.removeBlock(pos, false);
            } else {
                catalyst.remCount--;
            }
        }
    }

    /**
     * Scans every registered ritual and applies the first that fits: right core, complete frame,
     * and an effect that says it can run. Credits the owner when there is one.
     *
     * @return {@code true} if a ritual ran
     */
    public boolean activate(@Nullable ServerPlayer player) {
        if (level == null) {
            return false;
        }
        for (Ritual ritual : RitualRegistry.all()) {
            if (!ritual.isCoreComplete(level, worldPosition) || !ritual.isFrameComplete(level, worldPosition)
                    || !ritual.canApplyEffect(level, worldPosition)) {
                continue;
            }
            ritual.applyEffect(level, worldPosition);
            if (level instanceof ServerLevel server) {
                if (player != null) {
                    ritual.addAffinity(server, player);
                    // TileCatalyst rolled for Existence power a second time, on top of the
                    // 25 * tier loop the ritual itself already runs in addAffinity.
                    PlayerAffinity.addExistencePower(server.getRandom(), player);
                }
                // The effect already changed the world; say so where a player can see it.
                RitualFx.ritualFired(server, worldPosition, ritual.core());
            }
            setChanged();
            return true;
        }
        return false;
    }

    @Nullable
    private ServerPlayer onlineOwner(Level level) {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    // ---- state ----

    public void setOwner(Player player) {
        owner = player.getUUID();
        setChanged();
    }

    /** Sets the persistent per-tick handler; the ritual passes itself. */
    public void setHandler(Ritual ritual) {
        handlerId = RitualRegistry.all().indexOf(ritual);
        setChanged();
    }

    @Nullable
    public Ritual running() {
        var all = RitualRegistry.all();
        return handlerId >= 0 && handlerId < all.size() ? all.get(handlerId) : null;
    }

    public boolean isRunning() {
        return remCount != -1 || running() != null;
    }

    public int remCount() {
        return remCount;
    }

    public void setRemCount(int ticks) {
        remCount = ticks;
        setChanged();
    }

    @Nullable
    public Object[] data() {
        return data;
    }

    public void setData(Object[] data) {
        this.data = data;
        setChanged();
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        remCount = tag.getInt(TAG_REM_COUNT);
        handlerId = tag.contains(TAG_RUNNING) ? tag.getInt(TAG_RUNNING) : -1;
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        powered = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_REM_COUNT, remCount);
        tag.putInt(TAG_RUNNING, handlerId);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
    }
}
