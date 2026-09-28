package theflogat.technomancy.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import theflogat.technomancy.Technomancy;

/** The mod's one network channel; currently only the Existence HUD sync. */
public final class TechnomNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Technomancy.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private TechnomNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ExistenceSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExistenceSyncPacket::encode)
                .decoder(ExistenceSyncPacket::decode)
                .consumerMainThread(ExistenceSyncPacket::handle)
                .add();
    }

    public static void sendExistence(ServerPlayer player, int level, int power, int affinity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ExistenceSyncPacket(level, power, affinity));
    }
}
