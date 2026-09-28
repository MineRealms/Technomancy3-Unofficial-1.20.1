package theflogat.technomancy.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import theflogat.technomancy.common.registry.TechnomFluids;

/**
 * Client-only wiring for the Botania module. Fluid blocks cannot declare a {@code render_type} in
 * a model JSON the way the machine blocks do, so the mana fluid's translucent layer is set here.
 * The machines themselves are plain models with no block entity renderer.
 */
final class BotaniaClient {

    private BotaniaClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(BotaniaClient::clientSetup);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                TechnomFluids.MANA_BLOCK.get(), RenderType.translucent()));
    }
}
