package theflogat.technomancy.common.items.machines;

import java.util.function.Consumer;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import theflogat.technomancy.client.render.ElectricBellowsItemRenderer;

/**
 * The electric bellows' item, whose only job is the client hook.
 *
 * <p>An ordinary {@code BlockItem} would draw the block's JSON model, and that model is an empty
 * shell because the renderer draws the whole machine - so the icon would be blank. There is no
 * {@code RegisterClientExtensionsEvent} in this Forge build, so the extension is handed over
 * through {@code Item.initializeClient}, which {@code Item.initClient} reaches only on a physical
 * client.</p>
 */
public class ElectricBellowsItem extends BlockItem {

    public ElectricBellowsItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new ElectricBellowsItemRenderer.Extensions());
    }
}
