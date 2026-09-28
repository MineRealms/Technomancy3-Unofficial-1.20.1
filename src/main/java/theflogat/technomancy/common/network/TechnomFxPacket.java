package theflogat.technomancy.common.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: something happened at {@code pos} that only a client can draw.
 *
 * <p>Particles need no packet — {@code ServerLevel#sendParticles} already reaches the right
 * players. A bolt is geometry, and geometry belongs to whoever owns the render loop, so this
 * carries just enough to rebuild it: where, what colour, and which shape.</p>
 */
public record TechnomFxPacket(byte kind, BlockPos pos, int rgb) {

    /** A node came into being: four jagged bolts climbing off the block's corners. */
    public static final byte KIND_NODE = 0;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(kind);
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(rgb);
    }

    public static TechnomFxPacket decode(FriendlyByteBuf buffer) {
        return new TechnomFxPacket(buffer.readByte(), buffer.readBlockPos(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> theflogat.technomancy.client.fx.TechnomClientFx.accept(kind, pos, rgb)));
        ctx.setPacketHandled(true);
    }
}
