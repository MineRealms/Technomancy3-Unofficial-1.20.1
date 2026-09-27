package theflogat.technomancy.common.tiles.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaJarView;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * The creative jar: an endless source of one configured aspect, for testing and map making.
 *
 * <p>It has no contents to speak of, so it holds no store; every answer is derived from the
 * configured aspect. Being infinite does not exempt it from TC4R's contracts:
 * {@link #extractEssentia} returns exactly the request or zero ({@code EssentiaSource} is
 * all-or-nothing), {@link #takeEssentia} returns a value in {@code [0, amount]}
 * ({@code EssentiaApi.validateTransferResult}), and simulation changes nothing because nothing
 * ever changes. {@link CreativeJarContract} holds those rules and is unit tested.</p>
 *
 * <p>The original subclassed TC4's {@code TileJarFillable} and faked infinity by refilling to
 * 320 after every take; draining it completely nulled the aspect first and then "refilled" a
 * {@code null} aspect. It accepted nothing ({@code addToContainer} reported the whole amount as
 * left over), which is kept: this is a source, not a void.</p>
 */
public final class CreativeJarBlockEntity extends BlockEntity
        implements EssentiaTransport, EssentiaSource, EssentiaJarView {

    private static final String TAG_ASPECT = "aspect";

    @Nullable
    private AspectId aspect;

    public CreativeJarBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.CREATIVE_JAR.get(), pos, state);
    }

    /** The configured aspect if the registry still knows it. */
    @Nullable
    public AspectId aspect() {
        return aspect != null && AspectApi.contains(aspect) ? aspect : null;
    }

    /** @return {@code true} if the aspect changed */
    public boolean setAspect(@Nullable AspectId selected) {
        if (selected != null && !AspectApi.contains(selected)) {
            return false;
        }
        if (java.util.Objects.equals(selected, aspect)) {
            return false;
        }
        aspect = selected;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    // ---- essentia views ----

    @Override
    public AspectAmounts visibleAspects() {
        AspectId current = aspect();
        return current == null ? AspectAmounts.EMPTY : AspectAmounts.of(current, CreativeJarContract.DISPLAY_AMOUNT);
    }

    /** No label: the configured aspect is the contents, not a filter. */
    @Nullable
    @Override
    public AspectId filter() {
        return null;
    }

    @Override
    public boolean isConnectable(Direction face) {
        return CreativeJarContract.isOutputFace(face);
    }

    /** Never; see {@link #addEssentia}. */
    @Override
    public boolean canInputFrom(Direction face) {
        return false;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return CreativeJarContract.isOutputFace(face);
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return 0;
    }

    @Override
    public int minimumSuction() {
        return CreativeJarContract.MINIMUM_SUCTION;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return CreativeJarContract.take(aspect(), aspect, amount, face);
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int extractEssentia(AspectId aspect, int amount, EssentiaTransferMode mode) {
        return CreativeJarContract.extract(aspect(), aspect, amount);
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return canOutputTo(face) ? aspect() : null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return essentiaType(face) == null ? 0 : CreativeJarContract.DISPLAY_AMOUNT;
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return essentiaType(face);
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return CreativeJarContract.available(aspect(), aspect, face);
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ---- persistence and sync ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        aspect = null;
        if (tag.contains(TAG_ASPECT)) {
            try {
                aspect = AspectId.parse(tag.getString(TAG_ASPECT));
            } catch (IllegalArgumentException malformed) {
                aspect = null;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (aspect != null) {
            tag.putString(TAG_ASPECT, aspect.serialized());
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
