package theflogat.technomancy.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import theflogat.technomancy.client.render.CreativeJarRenderer;
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
