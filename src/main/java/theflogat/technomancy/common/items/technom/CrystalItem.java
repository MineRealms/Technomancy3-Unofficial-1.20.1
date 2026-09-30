package theflogat.technomancy.common.items.technom;

import java.util.function.Consumer;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import theflogat.technomancy.client.render.CrystalItemRenderer;

/**
 * The crystal's item, whose only job is the client hook.
 *
 * <p>A plain {@code BlockItem} would draw the block's JSON model, and that model is an empty shell
 * because the renderer draws the whole crystal - so the icon would be blank. There is no
 * {@code RegisterClientExtensionsEvent} in this Forge build, so the extension is handed over
 * through {@code Item.initializeClient}, which {@code Item.initClient} reaches only on a physical
 * client. Upstream reached the same drawing from {@code BlockCrystalRenderer.renderInventoryBlock},
 * which translated and then called {@code renderTileEntityAt} on a throwaway {@code TileCrystal}.</p>
 */
public class CrystalItem extends BlockItem {

    public CrystalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new CrystalItemRenderer.Extensions());
    }
}
