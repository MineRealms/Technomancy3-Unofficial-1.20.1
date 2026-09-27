package theflogat.technomancy.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import theflogat.technomancy.client.render.CreativeJarRenderer;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * Client wiring for the S2 machines-and-storage group, kept in one class so
 * {@link TechnomancyClient} only gains one call per hook.
 */
final class S2MachinesClient {

    private S2MachinesClient() {
    }

    /** Runs inside {@code FMLClientSetupEvent.enqueueWork}. */
    static void clientSetup() {
        ItemBlockRenderTypes.setRenderLayer(TechnomBlocks.CREATIVE_JAR.get(), RenderType.translucent());
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TechnomBlockEntities.CREATIVE_JAR.get(), CreativeJarRenderer::new);
    }
}
