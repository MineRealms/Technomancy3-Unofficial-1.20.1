package theflogat.technomancy.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * Client-only setup.
 *
 * <p>Loaded exclusively from {@link net.minecraftforge.fml.DistExecutor} on
 * {@link Dist#CLIENT}, so nothing here may be referenced from common code.</p>
 */
public final class TechnomancyClient {

    private TechnomancyClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TechnomancyClient::clientSetup);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        // Render layers are not part of the block state, so they must be declared here or the
        // block draws opaque no matter what the model says.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                TechnomBlocks.QUANTIZED_GLASS.get(), RenderType.translucent()));
    }
}
