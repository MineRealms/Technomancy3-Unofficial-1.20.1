package theflogat.technomancy.client.render.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A 1.7.10 Techne {@code ModelBase} rebuilt on the vanilla model-geometry system, so the original
 * block-entity models can be ported box-for-box instead of being re-invented as JSON.
 *
 * <p>The two formats line up exactly, which is the whole reason this class is a thin builder and
 * not a renderer of its own:</p>
 * <ul>
 *   <li>{@code ModelRenderer.addBox(x, y, z, w, h, d)} is {@link CubeListBuilder#addBox} - both
 *       treat {@code (x, y, z)} as the minimum corner in model units relative to the part.</li>
 *   <li>{@code ModelRenderer.setRotationPoint} plus {@code rotateAngleX/Y/Z} is
 *       {@link PartPose#offsetAndRotation}.</li>
 *   <li>{@code ModelRenderer.setTextureSize(w, h)} is the texture size passed to
 *       {@link LayerDefinition#create}.</li>
 *   <li>{@code ModelRenderer.mirror} is {@link CubeListBuilder#mirror}.</li>
 *   <li>The box unwrap is the same six-quad layout with the same {@code (u, v)} origin.</li>
 * </ul>
 *
 * <p>Two Techne quirks are modelled explicitly. First, {@code textureOffset}, {@code mirror} and
 * the rotation point live on the <em>renderer</em>, not on the box, so every box of one part
 * shares them - hence the fluent per-part builder. Second, Techne has no parent/child hierarchy:
 * each renderer is positioned in one flat space. Every part is therefore added directly under the
 * root, and a part's pose is relative to the block, never to another part.</p>
 *
 * <p>Y is <em>not</em> flipped here. {@code ModelPart} draws model coordinates as given, exactly
 * as {@code TileEntitySpecialRenderer} did; the y-down convention belongs to the entity renderers,
 * which apply their own {@code scale(-1, -1, 1)}. A port therefore translates the original
 * renderer's {@code glTranslatef}/{@code glScalef}/{@code glRotatef} calls onto the
 * {@link PoseStack} and changes nothing else.</p>
 *
 * <p>Bake once, in the block entity renderer's constructor, and keep the result: baking walks the
 * mesh and allocates the cube list, so it is not something to repeat per frame.</p>
 */
public final class TechneModel {

    /** The original's {@code 1F / 16F}: Techne model units are sixteenths of a block. */
    public static final float UNIT = 1.0F / 16.0F;

    private final ModelPart root;
    private final List<String> order;
    private final Map<String, ModelPart> parts;

    private TechneModel(ModelPart root, List<String> order, Map<String, ModelPart> parts) {
        this.root = root;
        this.order = order;
        this.parts = parts;
    }

    /** Starts a model whose texture sheet is {@code texWidth} by {@code texHeight} pixels. */
    public static Builder builder(int texWidth, int texHeight) {
        return new Builder(texWidth, texHeight);
    }

    /**
     * Bakes a one-part model, for the few renderers that draw a single detached box (the electric
     * bellows' bag is the only one upstream).
     */
    public static TechneModel single(int texWidth, int texHeight, String name,
            int u, int v, boolean mirror, float x, float y, float z, int w, int h, int d) {
        return builder(texWidth, texHeight)
                .part(name).uv(u, v).mirror(mirror).box(x, y, z, w, h, d).end()
                .build();
    }

    public ModelPart root() {
        return root;
    }

    public ModelPart part(String name) {
        ModelPart part = parts.get(name);
        if (part == null) {
            throw new IllegalArgumentException("no such part: " + name);
        }
        return part;
    }

    /**
     * Draws every part in declaration order, which is what the original {@code ModelBase.render()}
     * did. Declaration order matters whenever two boxes share a face, because Techne had no depth
     * sort either.
     */
    public void render(PoseStack pose, VertexConsumer vertices, int light, int overlay) {
        for (String name : order) {
            parts.get(name).render(pose, vertices, light, overlay);
        }
    }

    /** Draws one named part, for the models whose renderer animates or re-textures a single box. */
    public void render(String name, PoseStack pose, VertexConsumer vertices, int light, int overlay) {
        part(name).render(pose, vertices, light, overlay);
    }

    /**
     * Draws one named part with a flat colour multiplier. The flux lamp needs this and nothing
     * else does: the original wrapped only {@code renderCore()} in its {@code glColor3f} and left
     * the nozzles white, so the tint cannot be applied to the whole model.
     */
    public void render(String name, PoseStack pose, VertexConsumer vertices, int light, int overlay,
            float red, float green, float blue, float alpha) {
        part(name).render(pose, vertices, light, overlay, red, green, blue, alpha);
    }

    /**
     * Draws every part in declaration order with a flat colour multiplier, which is how the
     * original's {@code glColor4f} tints are reproduced. A negative scale or a translucent render
     * type is not needed for this; the colour goes straight onto the vertices.
     */
    public void render(PoseStack pose, VertexConsumer vertices, int light, int overlay,
            float red, float green, float blue, float alpha) {
        for (String name : order) {
            parts.get(name).render(pose, vertices, light, overlay, red, green, blue, alpha);
        }
    }

    /** Draws every part except the named ones, in declaration order. */
    public void renderExcept(PoseStack pose, VertexConsumer vertices, int light, int overlay,
            String... excluded) {
        outer:
        for (String name : order) {
            for (String skip : excluded) {
                if (skip.equals(name)) {
                    continue outer;
                }
            }
            parts.get(name).render(pose, vertices, light, overlay);
        }
    }

    /** A flat, order-preserving description of one Techne {@code ModelRenderer}. */
    private static final class PartSpec {
        private final String name;
        private final List<float[]> boxes = new ArrayList<>();
        private int u;
        private int v;
        private boolean mirror;
        private float px;
        private float py;
        private float pz;
        private float rx;
        private float ry;
        private float rz;

        private PartSpec(String name) {
            this.name = name;
        }
    }

    public static final class Builder {

        private final int texWidth;
        private final int texHeight;
        private final Map<String, PartSpec> specs = new LinkedHashMap<>();
        private PartSpec current;

        private Builder(int texWidth, int texHeight) {
            this.texWidth = texWidth;
            this.texHeight = texHeight;
        }

        /** Begins a part; every following {@code box} call belongs to it until {@link #end()}. */
        public Builder part(String name) {
            if (current != null) {
                throw new IllegalStateException("part " + current.name + " was never ended");
            }
            current = specs.computeIfAbsent(name, PartSpec::new);
            return this;
        }

        /** {@code ModelRenderer(model, texOffX, texOffY)}. */
        public Builder uv(int u, int v) {
            current.u = u;
            current.v = v;
            return this;
        }

        /** {@code ModelRenderer.mirror}. */
        public Builder mirror(boolean mirror) {
            current.mirror = mirror;
            return this;
        }

        /** {@code ModelRenderer.setRotationPoint}. */
        public Builder at(float x, float y, float z) {
            current.px = x;
            current.py = y;
            current.pz = z;
            return this;
        }

        /** {@code rotateAngleX/Y/Z}, in radians. */
        public Builder rotate(float x, float y, float z) {
            current.rx = x;
            current.ry = y;
            current.rz = z;
            return this;
        }

        /** {@code ModelRenderer.addBox}. */
        public Builder box(float x, float y, float z, int w, int h, int d) {
            current.boxes.add(new float[] {x, y, z, w, h, d});
            return this;
        }

        public Builder end() {
            current = null;
            return this;
        }

        public TechneModel build() {
            if (current != null) {
                throw new IllegalStateException("part " + current.name + " was never ended");
            }
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            Map<String, ModelPart> parts = new LinkedHashMap<>();
            for (Map.Entry<String, PartSpec> entry : specs.entrySet()) {
                PartSpec spec = entry.getValue();
                if (spec.boxes.isEmpty()) {
                    continue;
                }
                CubeListBuilder cubes = CubeListBuilder.create();
                // texOffs and mirror are per-renderer upstream, so they are set once per part and
                // every box of that part inherits them.
                cubes.texOffs(spec.u, spec.v);
                cubes.mirror(spec.mirror);
                for (float[] b : spec.boxes) {
                    cubes.addBox(b[0], b[1], b[2], (int) b[3], (int) b[4], (int) b[5]);
                }
                root.addOrReplaceChild(spec.name, cubes,
                        PartPose.offsetAndRotation(spec.px, spec.py, spec.pz, spec.rx, spec.ry, spec.rz));
            }
            ModelPart baked = LayerDefinition.create(mesh, texWidth, texHeight).bakeRoot();
            List<String> order = new ArrayList<>();
            for (String name : specs.keySet()) {
                if (baked.hasChild(name)) {
                    order.add(name);
                    parts.put(name, baked.getChild(name));
                }
            }
            return new TechneModel(baked, List.copyOf(order), Map.copyOf(parts));
        }
    }
}
