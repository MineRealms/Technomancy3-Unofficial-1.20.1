package theflogat.technomancy.common.items.nodes;

import java.util.function.Consumer;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import theflogat.technomancy.client.render.NodeFabricatorItemRenderer;

/**
 * The node fabricator's item form.
 *
 * <p>It exists only for {@link #initializeClient}. Upstream drew the item by handing it straight to
 * the block's renderer - see {@link NodeFabricatorItemRenderer} for why a JSON model cannot do the
 * job - and in this Forge build the hook for that is {@code Item.initializeClient}, not a
 * registration event. Forge reaches it from {@code Item.initClient} only when
 * {@code FMLEnvironment.dist == CLIENT}, so the client-only type referenced below is never resolved
 * on a dedicated server.</p>
 */
public class NodeFabricatorItem extends BlockItem {

    public NodeFabricatorItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new NodeFabricatorItemRenderer.Extensions());
    }
}
