package theflogat.technomancy.client.coils;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.blocks.coils.CoilBlock;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * Coil ring colours, replacing the original's {@code TileEssentiaTransmitterRenderer} /
 * {@code TileItemTransmitterRenderer}. Those drew a static model every frame only to tint two
 * rings; a JSON model with tint indices does the same without a block entity renderer.
 *
 * <ul>
 *   <li>tint 1, upper ring: red while a Potency Gem is installed, as the original.</li>
 *   <li>tint 0, lower ring: the essentia coil's filter aspect colour (added with that coil).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Technomancy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoilClient {

    static final int WHITE = 0xFFFFFF;
    static final int GEM_RED = 0xE02020;

    private CoilClient() {
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 1 ? gemColor(state) : WHITE,
                TechnomBlocks.ITEM_COIL.get());
    }

    static int gemColor(BlockState state) {
        return state.hasProperty(CoilBlock.GEM) && state.getValue(CoilBlock.GEM) ? GEM_RED : WHITE;
    }
}
