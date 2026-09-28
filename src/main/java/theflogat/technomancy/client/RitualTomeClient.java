package theflogat.technomancy.client;

import net.minecraft.client.Minecraft;

/** Client-only bridge so the item never references a client class on a server. */
public final class RitualTomeClient {

    private RitualTomeClient() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new theflogat.technomancy.client.screen.RitualTomeScreen());
    }
}
