//@wait 1
// Two client-side defects this round fixes are invisible to the server, to GameTest and to the
// data validator, because none of them ever bakes a model:
//
//   * the flux lamp was a plain cube. Its real body is thirteen boxes drawn in code, with a glass
//     core tinted by how full the tank is and a post-plus-flange pair on every side that is
//     actually plumbed - so the machine read as a solid block and said nothing about its plumbing;
//   * the shared coil model, which the essentia coil and the item coil both inherit, had its box
//     unwrap wrong on all eleven elements: `up` carried the rect that belongs to `down`, and `west`
//     and `east` were swapped. That shows up as an upside-down, mirrored texture on a block that
//     is in every base.
//
// It also guards the flux lamp's nozzle sides. The original's switch over ForgeDirection ordinals
// looks wrong, because the model's FrontPost sits at z = -7 and so reads as the north nozzle - but
// the whole model is drawn after scale(-1, -1, 1), which flips the rotation's output, and the chain
// in fact sends FrontPost to the west face, BackPost to the east, LeftPost to the north and
// RightPost to the south. That is exactly what the original's switch does, so this probe reads the
// four rotation points out of the renderer's own model and pushes them through the same transform.
//
// Two further defects are pinned here, both reported as "the flux lamp and the node fabricator are
// transparent in the hand and in the inventory, and the flux lamp is black in the world":
//
//   * a BlockEntityWithoutLevelRenderer item must have an item model that resolves to
//     builtin/entity. ItemRenderer branches on `model.isCustomRenderer()` and only then consults
//     IClientItemExtensions; a model that parents a block model with no elements bakes to an empty
//     BakedModel, so the custom renderer is never called and the icon is invisible. The probe asks
//     for the *baked* item model rather than the extension, because the extension was non-null the
//     whole time the bug was live;
//   * the core's tint is only applied when the tank holds something, as the original's
//     glColor3f did. Applying it unconditionally paints an empty lamp black.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);

// The id a model is baked under is not simply its file path - essentia_coil is only reachable as
// "inventory" - so ask the block state shaper instead, which is the same lookup the chunk renderer
// makes. Both of these blocks default to facing=down with no blockstate rotation, so the model is
// baked in its own orientation.
java.util.function.Function<String, net.minecraft.client.resources.model.BakedModel> modelOf = name -> {
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(new net.minecraft.resources.ResourceLocation("technom", name));
    if (block == null) {
        return null;
    }
    net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
    net.minecraft.client.resources.model.BakedModel model =
            mc.getModelManager().getBlockModelShaper().getBlockModel(state);
    return model == mc.getModelManager().getMissingModel() ? null : model;
};

java.util.function.Function<net.minecraft.client.resources.model.BakedModel, Integer> quads = model -> {
    net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
    return model.getQuads(null, null, r).size();
};

// DefaultVertexFormat.BLOCK: 8 ints per vertex - position (3), colour (1), uv0 (2), uv2 (1),
// normal (1) - so uv0 sits at index 4 and 5 and the position at 0..2. The uv a quad carries is in
// atlas space, so divide it back down onto the quad's own sprite to get the model's uv as a
// fraction of the whole sheet.
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

// ---- the coil: the base box's up face must sit to the right of its down face ----
net.minecraft.client.resources.model.BakedModel coil = modelOf.apply("essentia_coil");
if (coil == null || coil.isCustomRenderer()) {
    bad.add("essentia_coil has no baked model");
} else {
    out.append("essentia_coil: ").append(coil.getQuads(null, null, random).size() / 6)
       .append(" box(es)\n");
    net.minecraft.client.renderer.block.model.BakedQuad baseUp = null;
    net.minecraft.client.renderer.block.model.BakedQuad baseDown = null;
    for (net.minecraft.client.renderer.block.model.BakedQuad quad : coil.getQuads(null, null, random)) {
        // base is addBox(5, 0, 5, 6, 1, 6): its top is the only up face at y = 1/16 and its bottom
        // the only down face at y = 0.
        if (quad.getDirection() == net.minecraft.core.Direction.UP
                && Math.abs(centroidY.apply(quad) - 1.0F / 16.0F) < 1.0E-4F) {
            baseUp = quad;
        } else if (quad.getDirection() == net.minecraft.core.Direction.DOWN
                && Math.abs(centroidY.apply(quad)) < 1.0E-4F) {
            baseDown = quad;
        }
    }
    if (baseUp == null || baseDown == null) {
        bad.add("could not find the coil base's up and down quads");
    } else {
        // The sheet is 64x64 and the model's uv runs 0..16 across it, so the model uv divided by 16
        // is the fraction of the sprite a quad carries. base is at texOffs (0,0) with w = h = 1,
        // d = 6, so up = (U+Dx+Wu, V, U+Dx+2*Wu, V+Dy) = (6, 6.25, 7.5, 12.25) and
        // down = (U+Dx, V, U+Dx+Wu, V+Dy) = (4.5, 6.25, 6, 12.25) in 0..16 units... which is what
        // the generator wrote, one sixteenth of the sheet lower. What matters, and what the bug
        // broke, is the order: up must start to the RIGHT of down by exactly the box's width.
        float[] up = spriteUv.apply(baseUp);
        float[] down = spriteUv.apply(baseDown);
        out.append("coil base up uv:   [").append(up[0]).append(", ").append(up[1]).append(", ")
           .append(up[2]).append(", ").append(up[3]).append("]\n");
        out.append("coil base down uv: [").append(down[0]).append(", ").append(down[1]).append(", ")
           .append(down[2]).append(", ").append(down[3]).append("]\n");
        if (!(up[0] > down[0])) {
            bad.add("the coil base's up face starts at u = " + up[0] + " and its down face at u = "
                    + down[0] + "; up must start to the right of down, or the unwrap is swapped");
        }
        // w = 6 on a 64 wide sheet -> 6/64 of the sprite.
        float shift = 6.0F / 64.0F;
        if (Math.abs((up[0] - down[0]) - shift) > 1.0E-3F) {
            bad.add("the coil base's up face is " + (up[0] - down[0])
                    + " to the right of its down face, expected " + shift);
        }
        if (Math.abs(up[1] - down[1]) > 1.0E-3F || Math.abs(up[3] - down[3]) > 1.0E-3F) {
            bad.add("the coil base's up and down faces must share a v range, got [" + up[1] + ", "
                    + up[3] + "] and [" + down[1] + ", " + down[3] + "]");
        }
    }
}

// ---- the flux lamp: nothing in the model, everything in the renderer ----
net.minecraft.client.resources.model.BakedModel lampModel = modelOf.apply("flux_lamp");
if (lampModel == null) {
    bad.add("flux_lamp has no baked model");
} else {
    int boxes = quads.apply(lampModel) / 6;
    out.append("flux_lamp block: ").append(boxes).append(" box(es)\n");
    if (boxes != 0) {
        bad.add("flux_lamp's block model bakes " + boxes
                + " boxes, but the renderer draws all thirteen - they would be drawn twice");
    }
}

net.minecraft.world.level.block.Block lampBlock
        = theflogat.technomancy.common.registry.TechnomBlocks.FLUX_LAMP.get();
// newBlockEntity is on EntityBlock, not on Block.
net.minecraft.world.level.block.entity.BlockEntity lampEntity =
        ((net.minecraft.world.level.block.EntityBlock) lampBlock)
                .newBlockEntity(net.minecraft.core.BlockPos.ZERO, lampBlock.defaultBlockState());
out.append("flux_lamp renderer: ")
   .append(mc.getBlockEntityRenderDispatcher().getRenderer(lampEntity)).append("\n");
if (lampEntity == null || mc.getBlockEntityRenderDispatcher().getRenderer(lampEntity) == null) {
    bad.add("no BlockEntityRenderer registered for the flux lamp - its whole body would be invisible");
}

// The block's model is an empty shell now, so the item would have a blank icon unless it reaches
// the renderer too.
net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer lampItemRenderer =
        net.minecraftforge.client.extensions.common.IClientItemExtensions.of(lampBlock.asItem())
                .getCustomRenderer();
out.append("flux_lamp inventory renderer: ").append(lampItemRenderer).append("\n");
if (lampItemRenderer == null) {
    bad.add("the flux lamp item has no custom renderer, so its icon would be blank");
}

// The two checks above are not enough on their own, and this is the one that matters. ItemRenderer
// branches on the *baked model*: `if (!model.isCustomRenderer() && ...) renderModelLists(...) else
// IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(...)`. So a non-null extension
// is never consulted unless the item model resolves to builtin/entity, and an item model that
// parents a block model with no elements bakes to an empty BakedModel - which is exactly the
// transparent held and inventory icon that was reported. Assert the branch, not the wiring.
java.util.function.Function<net.minecraft.world.item.Item, net.minecraft.client.resources.model.BakedModel> itemModelOf
        = item -> mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), null, null, 0);
for (String name : new String[] {"flux_lamp", "node_fabricator"}) {
    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(new net.minecraft.resources.ResourceLocation("technom", name));
    net.minecraft.client.resources.model.BakedModel itemModel = itemModelOf.apply(block.asItem());
    out.append(name).append(" item model: ").append(itemModel).append(", customRenderer=")
       .append(itemModel.isCustomRenderer()).append("\n");
    if (!itemModel.isCustomRenderer()) {
        bad.add(name + "'s item model is not builtin/entity, so ItemRenderer takes the baked-model "
                + "path, never calls the custom renderer, and draws an empty model - a transparent "
                + "held and inventory icon");
    }
}

// ---- the nozzle sides, read out of the renderer's own model ----
// The renderer's chain is glScalef(-1,-1,1); glTranslatef(-.5,-1.5,.5); glRotatef(-90,0,1,0), and
// a PoseStack applies it to a point as scale(translate(rotate(p))). ModelPart divides its rotation
// point by 16 before using it.
try {
    java.lang.reflect.Field modelField =
            theflogat.technomancy.client.render.FluxLampRenderer.class.getDeclaredField("MODEL");
    modelField.setAccessible(true);
    Object model = modelField.get(null);
    java.lang.reflect.Method part = model.getClass().getMethod("part", String.class);
    String[] names = {"Core", "TopPost", "TopFlat", "BottomPost", "BottomFlat", "FrontPost",
            "FrontFlat", "RightPost", "RightFlat", "LeftPost", "LeftFlat", "BackPost", "BackFlat"};
    for (String name : names) {
        if (part.invoke(model, name) == null) {
            bad.add("the flux lamp model has no " + name + " part");
        }
    }
    // Expected world side of each horizontal nozzle: the original's switch sends the model's
    // FrontPost west and its BackPost east, because scale(-1, -1, 1) flips the rotation's output.
    String[][] expected = {{"FrontPost", "west", "0.4375"}, {"BackPost", "east", "0.25"},
            {"LeftPost", "north", "0.4375"}, {"RightPost", "south", "0.25"}};
    for (String[] row : expected) {
        net.minecraft.client.model.geom.ModelPart p =
                (net.minecraft.client.model.geom.ModelPart) part.invoke(model, row[0]);
        float px = p.x / 16.0F;
        float py = p.y / 16.0F;
        float pz = p.z / 16.0F;
        // rotateY(-90): (x, z) -> (-z, x); then translate; then scale(-1, -1, 1).
        float wx = -((-pz) - 0.5F);
        float wz = px + 0.5F;
        float offset = row[1].equals("west") ? 0.5F - wx
                : row[1].equals("east") ? wx - 0.5F
                : row[1].equals("north") ? 0.5F - wz : wz - 0.5F;
        out.append(row[0]).append(" -> world (x=").append(wx).append(", z=").append(wz)
           .append("), ").append(row[1]).append(" by ").append(offset).append("\n");
        if (offset <= 0.0F) {
            bad.add(row[0] + " lands on the wrong side of the lamp: it is " + offset
                    + " from the centre along " + row[1]);
        }
        if (Math.abs(offset - Float.parseFloat(row[2])) > 1.0E-3F) {
            bad.add(row[0] + " is " + offset + " from the centre along " + row[1] + ", expected "
                    + row[2] + " - the model's own rotation point moved");
        }
    }
} catch (ReflectiveOperationException reflected) {
    bad.add("could not read the flux lamp model reflectively: " + reflected);
}

// ---- the empty lamp must not be painted black ----
// The tint is the one part of the lamp that a model inspection cannot reach, so `draw` is called
// for real against a capturing MultiBufferSource and the colours it writes for the core are read
// back. ModelPart$Cube.compile pushes its vertices through VertexConsumer's 14-argument default
// `vertex`, which fans out to `color(float, float, float, float)` and so to the int overload below.
//
// This is the bug that was reported as "the flux lamp is black in the world": the original's
// glColor3f(amount, 0, amount) sat inside `if (tank.getFluidAmount() > 0)`, and dropping that guard
// tints an empty lamp (amount == 0) with (0, 0, 0, 1). The core goes through cutout, so the two
// thirds of its texels that are transparent are discarded and the surviving third is a solid black
// box where the glass should be.
final java.util.List<Integer> coreColours = new java.util.ArrayList<Integer>();
com.mojang.blaze3d.vertex.VertexConsumer capture = new com.mojang.blaze3d.vertex.VertexConsumer() {
    public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
        return this;
    }

    public com.mojang.blaze3d.vertex.VertexConsumer color(int r, int g, int b, int a) {
        // Packed ARGB, matching the order the arguments are written in.
        coreColours.add((r << 24) | (g << 16) | (b << 8) | a);
        return this;
    }

    public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) {
        return this;
    }

    public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) {
        return this;
    }

    public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    public void endVertex() {
    }

    public void defaultColor(int r, int g, int b, int a) {
    }

    public void unsetDefaultColor() {
    }
};
net.minecraft.client.renderer.MultiBufferSource captureBuffer = type -> capture;
com.mojang.blaze3d.vertex.PoseStack lampPose = new com.mojang.blaze3d.vertex.PoseStack();

theflogat.technomancy.client.render.FluxLampRenderer.draw(
        false, 0, null, null, lampPose, captureBuffer, 0xF000F0, 0);
out.append("empty flux lamp core: ").append(coreColours.size()).append(" vertex/vertices, colour 0x")
   .append(coreColours.isEmpty() ? "none" : Integer.toHexString(coreColours.get(0))).append("\n");
if (coreColours.isEmpty()) {
    bad.add("drawing an empty flux lamp wrote no vertices at all");
} else {
    for (int colour : coreColours) {
        if (colour != 0xFFFFFFFF) {
            bad.add("an empty flux lamp's core is tinted 0x" + Integer.toHexString(colour)
                    + " instead of untinted white 0xFFFFFFFF - that is the black lamp");
            break;
        }
    }
}

coreColours.clear();
theflogat.technomancy.client.render.FluxLampRenderer.draw(
        false, 500, null, null, lampPose, captureBuffer, 0xF000F0, 0);
out.append("full flux lamp core: colour 0x")
   .append(coreColours.isEmpty() ? "none" : Integer.toHexString(coreColours.get(0))).append("\n");
// glColor3f(amount, 0, amount) clamps 500 mB to (1, 0, 1), i.e. red 255, green 0, blue 255 - the
// packed ARGB form of that is 0xFF00FFFF, not 0xFFFF00FF, which would be yellow.
if (coreColours.isEmpty()) {
    bad.add("drawing a full flux lamp wrote no vertices at all");
} else if (coreColours.get(0) != 0xFF00FFFF) {
    bad.add("a full flux lamp's core is tinted 0x" + Integer.toHexString(coreColours.get(0))
            + ", expected magenta 0xFF00FFFF - the tank level no longer shows");
}

out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
