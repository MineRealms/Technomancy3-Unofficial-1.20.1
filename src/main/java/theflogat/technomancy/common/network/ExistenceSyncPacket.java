package theflogat.technomancy.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: the player's Existence level, power and dominant affinity ordinal. */
public record ExistenceSyncPacket(int level, int power, int affinity) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(level);
        buffer.writeVarInt(power);
        buffer.writeVarInt(affinity);
    }

    public static ExistenceSyncPacket decode(FriendlyByteBuf buffer) {
        return new ExistenceSyncPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> theflogat.technomancy.client.ExistenceHud.accept(level, power, affinity)));
        ctx.setPacketHandled(true);
    }
}
