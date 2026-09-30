//@wait 1
// The client-side defects this round fixes are invisible to the server, to GameTest and to the
// data validator, because none of them ever bakes a model:
//
//   * the existence pylons were a full cube of a fully transparent texture, so the item was
//     invisible and the block was a white box;
//   * the existence fountain was a full cube, because its real 31-box model was orphaned;
//   * the node fabricator was a 5-box invention on a 16x16 sprite, because its real 9-box
//     256x128 model was only reachable through a renderer;
//   * nothing tinted the pylon's floating cube, so all three tiers looked alike.
//
// It also guards the box unwrap. The hand-generated JSON models were built with a wrong
// ModelRenderer unwrap: the up face was given the rect that belongs to down, and down was given a
// copy of east. The authoritative rects, from ModelPart$Cube's constructor, are
//
//     down = (u+d,   v, u+d+w,   v+d)
//     up   = (u+d+w, v, u+d+w+w, v+d)
//
// so this probe reads the baked quads' own UVs for the pylon's base box, whose up and down faces
// are 8x8 model units at texOffs (0,0) on a 64x32 sheet.
//
// This probe reads the baked models and the colour handlers directly, so it fails if any of them
// regresses. Quad counts are exact: a block model emits six quads per element.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);

java.util.function.BiFunction<String, String, net.minecraft.client.resources.model.BakedModel> bake =
        (path, variant) -> mc.getModelManager().getModel(
                new net.minecraft.client.resources.model.ModelResourceLocation(
                        new net.minecraft.resources.ResourceLocation("technom", path), variant));

java.util.function.Function<net.minecraft.client.resources.model.BakedModel, Integer> quads = model -> {
    net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
    return model.getQuads(null, null, r).size();
};

java.util.function.Function<net.minecraft.client.resources.model.BakedModel, Integer> tinted = model -> {
    net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
    int count = 0;
    for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(null, null, r)) {
        if (quad.isTinted() && quad.getTintIndex() == 0) {
            count++;
        }
    }
    return count;
};

// DefaultVertexFormat.BLOCK: 8 ints per vertex - position (3), colour (1), uv0 (2), uv2 (1),
// normal (1) - so uv0 sits at index 4 and 5 and the position at 0..2. The uv a quad carries is in
// atlas space, so divide it back down onto the quad's own sprite to get the model's 0..16 uv
// divided by 16.
java.util.function.Function<net.minecraft.client.renderer.block.model.BakedQuad, float[]> spriteUv = quad -> {
    int[] v = quad.getVertices();
    net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = quad.getSprite();
    float su = sprite.getU1() - sprite.getU0();
    float sv = sprite.getV1() - sprite.getV0();
    float u0 = Float.MAX_VALUE;
    float u1 = -Float.MAX_VALUE;
    float v0 = Float.MAX_VALUE;
    float v1 = -Float.MAX_VALUE;
    for (int i = 0; i < 4; i++) {
        float u = (Float.intBitsToFloat(v[i * 8 + 4]) - sprite.getU0()) / su;
        float w = (Float.intBitsToFloat(v[i * 8 + 5]) - sprite.getV0()) / sv;
        u0 = Math.min(u0, u);
        u1 = Math.max(u1, u);
        v0 = Math.min(v0, w);
        v1 = Math.max(v1, w);
    }
    return new float[] {u0, v0, u1, v1};
};

java.util.function.Function<net.minecraft.client.renderer.block.model.BakedQuad, Float> centroidY = quad -> {
    int[] v = quad.getVertices();
    float y = 0.0F;
    for (int i = 0; i < 4; i++) {
        y += Float.intBitsToFloat(v[i * 8 + 1]);
    }
    return y / 4.0F;
};

// ---- the pylons: 5 boxes, and the floating cube's 6 faces are the only tinted ones ----
for (String tier : new String[] {"basic", "advanced", "complex"}) {
    net.minecraft.client.resources.model.BakedModel model = bake.apply("existence_pylon_" + tier, "");
    if (model == null || model.isCustomRenderer()) {
        bad.add("existence_pylon_" + tier + " has no baked model");
        continue;
    }
    int boxes = quads.apply(model) / 6;
    int tintedFaces = tinted.apply(model);
    out.append("existence_pylon_").append(tier).append(": ")
       .append(boxes).append(" box(es), ").append(tintedFaces).append(" tinted face(s)\n");
    if (boxes != 5) {
        bad.add("existence_pylon_" + tier + " bakes " + boxes + " boxes, expected 5");
    }
    if (tintedFaces != 6) {
        bad.add("existence_pylon_" + tier + " has " + tintedFaces
                + " tinted faces, expected the cube's 6 - the tier colour would not show");
    }
}

// None of these faces declares a cullface, so every quad lands in BlockModel's unculledFaces list
// and is only reachable through a null direction; the per-direction lists stay empty. Filter on
// the quad's own direction instead.
net.minecraft.client.resources.model.BakedModel pylon = bake.apply("existence_pylon_basic", "");
net.minecraft.client.renderer.block.model.BakedQuad baseUp = null;
net.minecraft.client.renderer.block.model.BakedQuad baseDown = null;
java.util.List<String> upCentroids = new java.util.ArrayList<String>();
java.util.List<String> downCentroids = new java.util.ArrayList<String>();
for (net.minecraft.client.renderer.block.model.BakedQuad quad : pylon.getQuads(null, null, random)) {
    if (quad.getDirection() == net.minecraft.core.Direction.UP) {
        float y = centroidY.apply(quad);
        upCentroids.add(String.valueOf(y));
        // The base is the only box whose top is at y = 1, i.e. 1/16 of a block.
        if (Math.abs(y - 1.0F / 16.0F) < 1.0E-4F) {
            baseUp = quad;
        }
    } else if (quad.getDirection() == net.minecraft.core.Direction.DOWN) {
        float y = centroidY.apply(quad);
        downCentroids.add(String.valueOf(y));
        if (Math.abs(y) < 1.0E-4F) {
            baseDown = quad;
        }
    }
}
if (baseUp == null || baseDown == null) {
    bad.add("could not find the pylon base's up and down quads"
            + " (up centroidY = " + upCentroids + ", down centroidY = " + downCentroids + ")");
} else {
    // base is addBox(-4, 0, -4, 8, 1, 8) at texOffs (0,0) on a 64x32 sheet. Model uv spans 0..16
    // across the whole sprite, so dividing by 16 gives the sprite-relative uv a quad carries.
    // up   = (u+d+w, v, u+d+w+w, v+d) = (16, 0, 24, 8) px = (4, 0, 6, 4) -> /16
    // down = (u+d,   v, u+d+w,   v+d) = ( 8, 0, 16, 8) px = (2, 0, 4, 4) -> /16
    float[] up = spriteUv.apply(baseUp);
    float[] down = spriteUv.apply(baseDown);
    out.append("pylon base up uv:   [").append(up[0]).append(", ").append(up[1]).append(", ")
       .append(up[2]).append(", ").append(up[3]).append("]\n");
    out.append("pylon base down uv: [").append(down[0]).append(", ").append(down[1]).append(", ")
       .append(down[2]).append(", ").append(down[3]).append("]\n");
    if (Math.abs(up[0] - 0.25F) > 1.0E-3F || Math.abs(up[2] - 0.375F) > 1.0E-3F
            || Math.abs(up[1]) > 1.0E-3F || Math.abs(up[3] - 0.25F) > 1.0E-3F) {
        bad.add("the pylon base's up face uses uv [" + up[0] + ", " + up[1] + ", " + up[2] + ", "
                + up[3] + "], expected [0.25, 0.0, 0.375, 0.25] - the unwrap is wrong");
    }
    if (Math.abs(down[0] - 0.125F) > 1.0E-3F || Math.abs(down[2] - 0.25F) > 1.0E-3F
            || Math.abs(down[1]) > 1.0E-3F || Math.abs(down[3] - 0.25F) > 1.0E-3F) {
        bad.add("the pylon base's down face uses uv [" + down[0] + ", " + down[1] + ", " + down[2]
                + ", " + down[3] + "], expected [0.125, 0.0, 0.25, 0.25] - the unwrap is wrong");
    }
}

// ---- the fountain: the orphaned 31-box model must now be the one the blockstate names ----
net.minecraft.client.resources.model.BakedModel fountain = bake.apply("existence_fountain", "");
if (fountain == null || fountain.isCustomRenderer()) {
    bad.add("existence_fountain has no baked model");
} else {
    int boxes = quads.apply(fountain) / 6;
    out.append("existence_fountain: ").append(boxes).append(" box(es)\n");
    if (boxes != 31) {
        bad.add("existence_fountain bakes " + boxes + " boxes, expected the original's 31");
    }
}

// ---- the node fabricator: both models are empty shells, and the item must reach the renderer ----
net.minecraft.client.resources.model.BakedModel fabricatorBlock = bake.apply("node_fabricator", "facing=north");
if (fabricatorBlock == null) {
    bad.add("node_fabricator has no baked block model");
} else {
    int boxes = quads.apply(fabricatorBlock) / 6;
    out.append("node_fabricator block: ").append(boxes).append(" box(es)\n");
    if (boxes != 0) {
        bad.add("node_fabricator's block model bakes " + boxes
                + " boxes, but the renderer draws all nine - they would be drawn twice");
    }
}
net.minecraft.client.resources.model.BakedModel fabricatorItem = bake.apply("node_fabricator", "inventory");
if (fabricatorItem == null) {
    bad.add("node_fabricator has no baked item model");
} else {
    int boxes = quads.apply(fabricatorItem) / 6;
    out.append("node_fabricator item: ").append(boxes).append(" box(es)\n");
    if (boxes != 0) {
        bad.add("node_fabricator's item model bakes " + boxes
                + " boxes, but the block entity renderer draws all nine - they would be drawn twice");
    }
}
net.minecraft.world.item.Item fabricatorItemObject
        = theflogat.technomancy.common.registry.TechnomBlocks.NODE_FABRICATOR.get().asItem();
net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer inventoryRenderer
        = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(fabricatorItemObject)
                .getCustomRenderer();
out.append("node_fabricator inventory renderer: ").append(inventoryRenderer).append("\n");
if (inventoryRenderer == null) {
    bad.add("the node fabricator item has no custom renderer, so its icon would be blank");
}

// ---- the tier tints, through both the block and the item colour handler ----
int[] expected = {0x0000B3, 0x008080, 0x00B300};
net.minecraft.world.level.block.Block[] pylonBlocks = {
        theflogat.technomancy.common.registry.TechnomBlocks.EXISTENCE_PYLON_BASIC.get(),
        theflogat.technomancy.common.registry.TechnomBlocks.EXISTENCE_PYLON_ADVANCED.get(),
        theflogat.technomancy.common.registry.TechnomBlocks.EXISTENCE_PYLON_COMPLEX.get()};
for (int i = 0; i < pylonBlocks.length; i++) {
    net.minecraft.world.level.block.state.BlockState state = pylonBlocks[i].defaultBlockState();
    int blockTint = mc.getBlockColors().getColor(state, null, null, 0);
    int itemTint = mc.getItemColors().getColor(new net.minecraft.world.item.ItemStack(pylonBlocks[i]), 0);
    out.append("tier ").append(i).append(" tints: block=0x")
       .append(Integer.toHexString(blockTint)).append(" item=0x")
       .append(Integer.toHexString(itemTint)).append("\n");
    if ((blockTint & 0xFFFFFF) != expected[i]) {
        bad.add("the world tint of pylon tier " + i + " is 0x" + Integer.toHexString(blockTint)
                + ", expected 0x" + Integer.toHexString(expected[i]));
    }
    if ((itemTint & 0xFFFFFF) != expected[i]) {
        bad.add("the inventory tint of pylon tier " + i + " is 0x" + Integer.toHexString(itemTint)
                + ", expected 0x" + Integer.toHexString(expected[i]));
    }
}

// ---- the block entity types whose renderer draws geometry a model cannot ----
java.util.List<String> rendererTypes = new java.util.ArrayList<String>();
for (net.minecraft.world.level.block.entity.BlockEntityType<?> type
        : net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE) {
    net.minecraft.resources.ResourceLocation id
            = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    if (id != null && id.getNamespace().equals("technom")
            && (id.getPath().equals("node_fabricator") || id.getPath().equals("existence_fountain")
                    || id.getPath().equals("quantum_jar"))) {
        rendererTypes.add(id.getPath());
    }
}
out.append("code-drawn block entities present: ").append(rendererTypes).append("\n");
if (rendererTypes.size() != 3) {
    bad.add("expected node_fabricator, existence_fountain and quantum_jar block entity types, found "
            + rendererTypes);
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
