package theflogat.technomancy.common.tiles.nodes;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * One of the eight shell blocks of a node fabricator: a port into the controller, and nothing of
 * its own.
 *
 * <p>Ported from {@code TileFakeAirNG} (and the {@code TileFakeAirCore} base it shared with the
 * catalyst's fake light air - see the design note). Every question is answered by the controller:
 * the Forge Energy capability handed out here <em>is</em> the controller's, so the ledger, the
 * per-tick budget and the essentia store are one set of accounts however many faces a cable finds
 * (matrix row: all FE/EU ports must share the host's storage and budget).</p>
 *
 * <p>The watchdog is the original's: if the host is gone, the shell takes itself down. It never
 * loads the host's chunk to find out, and while that chunk is unloaded the ports simply report
 * empty instead of inventing a store of their own.</p>
 */
public final class NodeFabricatorShellBlockEntity extends BlockEntity implements EssentiaTransport, AspectContainerView {

    private static final String TAG_HOST = "Host";

    @Nullable
    private BlockPos host;

    public NodeFabricatorShellBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.NODE_FABRICATOR_SHELL.get(), pos, state);
    }

    @Nullable
    public BlockPos host() {
        return host;
    }

    public void setHost(BlockPos controller) {
        BlockPos immutable = controller.immutable();
        if (!immutable.equals(host)) {
            host = immutable;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    /** The controller, or {@code null} when it is gone, unloaded or no longer claims this shell. */
    @Nullable
    public NodeFabricatorBlockEntity controller() {
        if (host == null || level == null || !level.isLoaded(host)) {
            return null;
        }
        if (!(level.getBlockEntity(host) instanceof NodeFabricatorBlockEntity machine)) {
            return null;
        }
        return machine.structurePositions().contains(worldPosition) ? machine : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NodeFabricatorShellBlockEntity shell) {
        if (shell.host == null) {
            level.removeBlock(pos, false);
            return;
        }
        // Only an actually loaded host counts as missing; an unloaded chunk is not an answer, and
        // asking for it would load chunks in a ring around every fabricator.
        if (level.isLoaded(shell.host) && shell.controller() == null) {
            level.removeBlock(pos, false);
        }
    }

    // ---- energy: the host's own capability, never a copy ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        NodeFabricatorBlockEntity machine = controller();
        if (machine != null) {
            LazyOptional<T> view = machine.energy().getCapability(cap, side);
            if (view.isPresent()) {
                return view;
            }
        }
        return super.getCapability(cap, side);
    }

    // ---- essentia: forwarded with this shell's own face rules ----

    @Override
    public AspectAmounts visibleAspects() {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? AspectAmounts.EMPTY : machine.visibleAspects();
    }

    @Override
    public List<AspectId> visibleAspectOrder() {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? List.of() : machine.visibleAspectOrder();
    }

    @Override
    public boolean isConnectable(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine != null && machine.isConnectable(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine != null && machine.canInputFrom(face);
    }

    @Override
    public boolean canOutputTo(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine != null && machine.canOutputTo(face);
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? null : machine.suctionType(face);
    }

    @Override
    public int suctionAmount(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.suctionAmount(face);
    }

    @Override
    public int minimumSuction() {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.minimumSuction();
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.takeEssentia(aspect, amount, face, mode);
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.addEssentia(aspect, amount, face, mode);
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? null : machine.essentiaType(face);
    }

    @Override
    public int essentiaAmount(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.essentiaAmount(face);
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? null : machine.extractableAspect(face);
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        NodeFabricatorBlockEntity machine = controller();
        return machine == null ? 0 : machine.availableEssentia(aspect, face);
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        host = tag.contains(TAG_HOST, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_HOST)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (host != null) {
            tag.put(TAG_HOST, NbtUtils.writeBlockPos(host));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
