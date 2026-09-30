package theflogat.technomancy.client;

import java.util.List;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.common.blocks.technom.existence.ExistencePylonBlock;
import theflogat.technomancy.common.registry.TechnomBlocks;

/**
 * Client wiring for the S3 Existence network, kept in one class so {@link TechnomancyClient} only
 * gains one call per hook.
 *
 * <p>The only thing here is the pylon's floating cube. Upstream drew the pylon entirely from a
 * {@code TileEntitySpecialRenderer} and picked the cube's colour in
 * {@code ModelExistencePylon.renderCube} with a {@code glColor3f} per metadata value. The cube is
 * static geometry, so it is a plain JSON element instead, and the three triples become the block
 * and item tint the model's {@code "tintindex": 0} asks for. Tinting rather than three pre-dyed
 * textures is what keeps one model for all three tiers, exactly as upstream had one model for all
 * three metadata values.</p>
 *
 * <p>Both handlers are needed. A block colour handler alone tints the pylon in the world but
 * leaves the item grey, because {@code ItemRenderer} consults {@code ItemColors} and never
 * {@code BlockColors}; vanilla bridges the two by hand for the handful of items that need it, and
 * so must every mod.</p>
 */
final class S3ExistenceClient {

    private S3ExistenceClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(S3ExistenceClient::registerBlockColors);
        modBus.addListener(S3ExistenceClient::registerItemColors);
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (RegistryObject<Block> pylon : pylons()) {
            event.register((state, level, pos, tint) ->
                    tint == 0 && state.getBlock() instanceof ExistencePylonBlock block
                            ? block.tier().cubeTint() : -1,
                    pylon.get());
        }
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (RegistryObject<Block> pylon : pylons()) {
            event.register((stack, tint) ->
                    tint == 0 && pylon.get() instanceof ExistencePylonBlock block
                            ? block.tier().cubeTint() : -1,
                    pylon.get().asItem());
        }
    }

    private static List<RegistryObject<Block>> pylons() {
        return List.of(
                TechnomBlocks.EXISTENCE_PYLON_BASIC,
                TechnomBlocks.EXISTENCE_PYLON_ADVANCED,
                TechnomBlocks.EXISTENCE_PYLON_COMPLEX);
    }
}
