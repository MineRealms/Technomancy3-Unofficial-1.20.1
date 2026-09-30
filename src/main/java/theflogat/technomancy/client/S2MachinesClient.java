package theflogat.technomancy.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import theflogat.technomancy.client.render.AdvDeconTableRenderer;
import theflogat.technomancy.client.render.BiomeMorpherRenderer;
import theflogat.technomancy.client.render.CreativeJarRenderer;
import theflogat.technomancy.client.render.EldritchConsumerRenderer;
import theflogat.technomancy.client.render.ElectricBellowsRenderer;
import theflogat.technomancy.client.render.EssentiaFusorRenderer;
import theflogat.technomancy.client.render.FluxLampRenderer;
import theflogat.technomancy.client.render.NodeDynamoRenderer;
import theflogat.technomancy.client.screen.ProcessorScreen;
import theflogat.technomancy.common.items.PureOreItem;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomMenus;
import theflogat.technomancy.common.registry.TechnomPureOres;

/**
 * Client wiring for the S2 machines-and-storage group, kept in one class so
 * {@link TechnomancyClient} only gains one call per hook.
 */
final class S2MachinesClient {

    private S2MachinesClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(S2MachinesClient::registerItemColors);
    }

    /** Runs inside {@code FMLClientSetupEvent.enqueueWork}, which is where both of these belong. */
    static void clientSetup() {
        ItemBlockRenderTypes.setRenderLayer(TechnomBlocks.CREATIVE_JAR.get(), RenderType.translucent());
        MenuScreens.register(TechnomMenus.PROCESSOR.get(), ProcessorScreen::new);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TechnomBlockEntities.CREATIVE_JAR.get(), CreativeJarRenderer::new);
        // The fusor's whole configuration is invisible without this.
        event.registerBlockEntityRenderer(TechnomBlockEntities.ESSENTIA_FUSOR.get(), EssentiaFusorRenderer::new);
        // The lamp's thirteen boxes, its per-side nozzles and its fill tint are all code-drawn.
        event.registerBlockEntityRenderer(TechnomBlockEntities.FLUX_LAMP.get(), FluxLampRenderer::new);
        // These five machines are the same story: upstream's ISimpleBlockRenderingHandler returned
        // false from renderWorldBlock, so the blockstate model is an empty shell and every pixel
        // comes from the renderer. See each class for why a JSON model cannot stand in.
        event.registerBlockEntityRenderer(TechnomBlockEntities.ADV_DECON_TABLE.get(),
                AdvDeconTableRenderer::new);
        event.registerBlockEntityRenderer(TechnomBlockEntities.ELDRITCH_CONSUMER.get(),
                EldritchConsumerRenderer::new);
        event.registerBlockEntityRenderer(TechnomBlockEntities.ELECTRIC_BELLOWS.get(),
                ElectricBellowsRenderer::new);
        event.registerBlockEntityRenderer(TechnomBlockEntities.BIOME_MORPHER.get(),
                BiomeMorpherRenderer::new);
        event.registerBlockEntityRenderer(TechnomBlockEntities.NODE_DYNAMO.get(),
                NodeDynamoRenderer::new);
    }

    /**
     * The purified ore textures are grayscale and tinted per material, as the original did — it
     * averaged the ingot texture's pixels at startup, which is client-only work that cannot run
     * on a dedicated server, so the colours are fixed values now.
     */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (Item item : TechnomPureOres.all()) {
            event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof PureOreItem ore
                    ? ore.material().tint() : -1, item);
        }
    }
}
