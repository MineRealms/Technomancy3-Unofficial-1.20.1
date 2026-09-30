// Every blockstate and every item we register must have a real baked model and real
// sprites. A missing one is not an error on a client - Minecraft silently substitutes a
// placeholder and carries on - so it has to be asserted, not waited for.
//
// This walks EVERY possible blockstate, not just the default, because a blockstate file
// that forgets a variant (or names a property the block does not have) only breaks for
// the combinations it missed.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

if (mc.level == null) {
    return "FAIL: the client is not in a world, so nothing is baked yet\n";
}
out.append("client in world, ").append(mc.level.dimension().location()).append("\n");

net.minecraft.client.resources.model.ModelManager models = mc.getModelManager();
net.minecraft.client.resources.model.BakedModel missing = models.getMissingModel();
net.minecraft.client.renderer.texture.TextureAtlasSprite missingSprite =
        mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(new net.minecraft.resources.ResourceLocation("minecraft", "missingno"));

int states = 0;
int blocks = 0;
for (net.minecraft.resources.ResourceLocation id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
    if (!id.getNamespace().equals("technom")) {
        continue;
    }
    blocks++;
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
    for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
        states++;
        net.minecraft.client.resources.model.BakedModel baked =
                models.getBlockModelShaper().getBlockModel(state);
        if (baked == missing) {
            bad.add("no baked model for " + state);
            continue;
        }
        // The particle icon is what breaking and running into the block uses; a missing
        // one means the model resolved but its textures did not.
        net.minecraft.client.renderer.texture.TextureAtlasSprite particle =
                baked.getParticleIcon();
        if (particle == missingSprite || particle.contents().name().getPath().contains("missingno")) {
            bad.add("missing texture on " + state + " (particle icon is missingno)");
        }
    }
}
out.append("blocks=").append(blocks).append(" states=").append(states).append("\n");

int items = 0;
for (net.minecraft.resources.ResourceLocation id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
    if (!id.getNamespace().equals("technom")) {
        continue;
    }
    items++;
    net.minecraft.world.item.ItemStack stack =
            new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
    net.minecraft.client.resources.model.BakedModel baked =
            mc.getItemRenderer().getModel(stack, mc.level, null, 0);
    if (baked == missing) {
        bad.add("no item model for " + id);
    } else if (baked.getParticleIcon().contents().name().getPath().contains("missingno")) {
        // The same atlas rule as the blocks above, which the item side used to be left out of:
        // a builtin/entity item model goes through the block model loader, so its particle has
        // to name a texture the block atlas stitches. The five crystals named textures/entity/.
        bad.add("missing particle texture on item " + id);
    }
}
out.append("items=").append(items).append("\n");

// Translucent blocks must end up on a translucent chunk layer or they draw opaque.
//
// Asserted through BakedModel.getRenderTypes, which is what the chunk renderer actually
// consults. Do NOT use ItemBlockRenderTypes.getChunkRenderType here: Forge annotates it
// "does NOT support model-based render types", and it reads a different legacy map, so it
// reports solid for a model that declares "render_type": "minecraft:translucent" and is
// rendering translucent perfectly well.
net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
String[] translucent = {"quantized_glass", "quantum_jar"};
for (String name : translucent) {
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(new net.minecraft.resources.ResourceLocation("technom", name));
    net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
    net.minecraftforge.client.ChunkRenderTypeSet types = models.getBlockModelShaper()
            .getBlockModel(state)
            .getRenderTypes(state, random, net.minecraftforge.client.model.data.ModelData.EMPTY);
    boolean isTranslucent = types.contains(net.minecraft.client.renderer.RenderType.translucent());
    out.append("  render types of ").append(name).append(" = ").append(types.asList())
       .append(isTranslucent ? " (translucent)" : " (NOT translucent)").append("\n");
    if (!isTranslucent) {
        bad.add(name + " is not on the translucent layer, so it will draw opaque: " + types.asList());
    }
}

if (blocks == 0 || items == 0) {
    bad.add("nothing of ours is registered on the client - is the mod loaded?");
}
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
