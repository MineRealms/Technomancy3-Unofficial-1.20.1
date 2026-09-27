package theflogat.technomancy.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import theflogat.technomancy.client.render.QuantumJarRenderer;
import theflogat.technomancy.common.registry.TechnomBlockEntities;

/**
 * Client-only setup.
 *
 * <p>Loaded exclusively from {@link net.minecraftforge.fml.DistExecutor} on
 * {@link Dist#CLIENT}, so nothing here may be referenced from common code.</p>
 *
 * <p>Chunk render types are NOT set here. They are declared as {@code "render_type"} in each
 * block model's JSON, which is the documented way in 1.20.1 and the one the chunk renderer
 * actually consults, through {@code BakedModel.getRenderTypes}. Forge's
 * {@code ItemBlockRenderTypes.setRenderLayer} still works but is deprecated for removal, and
 * it writes a block-keyed map that model-based render types bypass - so having both invites
 * the two declarations to disagree.</p>
 */
public final class TechnomancyClient {

    private TechnomancyClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TechnomancyClient::registerRenderers);
    }

    private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        // Only the essentia level and the label need drawing in code; the shell is a JSON model.
        event.registerBlockEntityRenderer(TechnomBlockEntities.QUANTUM_JAR.get(), QuantumJarRenderer::new);
    }
}
