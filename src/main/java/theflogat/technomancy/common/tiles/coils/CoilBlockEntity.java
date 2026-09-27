package theflogat.technomancy.common.tiles.coils;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.coils.CoilBlock;
import theflogat.technomancy.common.coils.CoilLink;
import theflogat.technomancy.common.coils.CoilLinks;
import theflogat.technomancy.common.coils.Couplable;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.tiles.base.RedstoneControl;

/**
 * Shared behaviour of the two coils. Ported from {@code TileCoilTransmitter}.
 *
 * <p>A coil sits against one block (its {@link CoilBlock#FACING}), and every tick draws from each
 * linked block into that one. Links are positions only; every tick re-resolves them and never
 * keeps a block entity. A link whose chunk is unloaded is skipped and kept; a loaded link whose
 * block no longer qualifies is dropped, as the original did.</p>
 *
 * <p>Redstone: runs while unpowered by default ({@code RedstoneSet.LOW} in the original), and the
 * mode is programmable like every other machine. With a Potency Gem installed the coil instead
 * emits a redstone signal while its target still has room, and ignores redstone itself, because
 * its own signal would otherwise switch it off. The original enforced that by resetting the mode
 * to {@code NONE} and spitting out the programming item; here the stored mode is simply not
 * consulted while the gem is in, so removing the gem restores what the player had set.</p>
 */
public abstract class CoilBlockEntity extends BlockEntity implements Couplable {

    private static final String TAG_LINKS = "Links";
    private static final String TAG_REDSTONE = "Redstone";
    private static final String TAG_SIGNAL = "Signal";

    protected final CoilLinks links = new CoilLinks();
    private final RedstoneControl redstone = new RedstoneControl(RedstoneMode.LOW);
    private boolean signalling;

    protected CoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        redstone.setListener(this::setChanged);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoilBlockEntity coil) {
        coil.tick(level);
    }

    private void tick(Level level) {
        Direction facing = facing();
        BlockPos targetPos = worldPosition.relative(facing);
        // Never touch an unloaded neighbour: getBlockEntity would load its chunk.
        boolean targetLoaded = level.isLoaded(targetPos);
        setSignalling(hasGem() && targetLoaded && targetHasRoom(level, targetPos, facing));
        if (!targetLoaded || links.isEmpty() || !canRun()) {
            return;
        }
        boolean pruned = false;
        for (CoilLink link : links.rotation()) {
            if (link.pos().equals(targetPos) || !level.isLoaded(link.pos())) {
                // Feeding a block from itself would only churn; an unloaded link is kept for later.
                continue;
            }
            if (!isValidSource(level, link.pos(), link.face())) {
                links.remove(link.pos());
                pruned = true;
                continue;
            }
            drawFrom(level, link, targetPos, facing);
        }
        if (pruned) {
            linksChanged();
        }
    }

    // ---- what the two coils differ in ----

    /** Whether a linked block is still something this coil can draw from. */
    protected abstract boolean isValidSource(Level level, BlockPos pos, @Nullable Direction face);

    /** One transfer pass from one link into the target; the target chunk is loaded. */
    protected abstract void drawFrom(Level level, CoilLink link, BlockPos targetPos, Direction facing);

    /** For the Potency Gem signal: whether the target could take anything more. */
    protected abstract boolean targetHasRoom(Level level, BlockPos targetPos, Direction facing);

    @Override
    public boolean acceptsLinkTarget(Level level, BlockPos target, Direction face) {
        return !(level.getBlockEntity(target) instanceof CoilBlockEntity) && isValidSource(level, target, face);
    }

    // ---- state ----

    public Direction facing() {
        return getBlockState().getValue(CoilBlock.FACING);
    }

    public boolean hasGem() {
        return getBlockState().getValue(CoilBlock.GEM);
    }

    public boolean canRun() {
        return level != null && (hasGem() || redstone.canRun(level, worldPosition));
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    public boolean isSignalling() {
        return signalling;
    }

    private void setSignalling(boolean on) {
        if (signalling == on || level == null) {
            return;
        }
        signalling = on;
        setChanged();
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public CoilLinks links() {
        return links;
    }

    @Override
    public void linksChanged() {
        setChanged();
    }

    /** Called when something the client draws changed. */
    protected void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- persistence and sync ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int rejected = links.load(tag.getCompound(TAG_LINKS), worldPosition);
        if (rejected > 0) {
            Technomancy.LOGGER.warn("Coil at {} dropped {} saved link(s) that break the link rules",
                    worldPosition, rejected);
        }
        redstone.load(tag.contains(TAG_REDSTONE) ? tag.getCompound(TAG_REDSTONE) : null);
        signalling = tag.getBoolean(TAG_SIGNAL);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_LINKS, links.save());
        CompoundTag mode = new CompoundTag();
        redstone.save(mode);
        tag.put(TAG_REDSTONE, mode);
        tag.putBoolean(TAG_SIGNAL, signalling);
    }

    /** Clients only need what they draw; the link list stays on the server. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeClientData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readClientData(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        readClientData(tag == null ? new CompoundTag() : tag);
        if (level != null) {
            // Re-mesh, so a block colour that depends on this data is redrawn.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Subclasses add their filter; the base has nothing the client draws. */
    protected void writeClientData(CompoundTag tag) {
    }

    protected void readClientData(CompoundTag tag) {
    }
}
