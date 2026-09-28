package theflogat.technomancy.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import theflogat.technomancy.common.network.TechnomFxPacket;

/**
 * Client-drawn effects: the bolts a node creation shoots up.
 *
 * <p>Particles need none of this — the server sends those and they arrive on their own. A bolt is
 * geometry, so the server only says "one happened here, in this colour" and the client rebuilds
 * and draws it. The jitter is seeded from the position, not from a random source, so every client
 * watching the same creation sees the same bolt rather than four different ones.</p>
 *
 * <p>Everything here is client-only and reached only through {@link TechnomFxPacket#handle}'s
 * {@code DistExecutor}, or from a client event subscriber, so a dedicated server never loads it.</p>
 */
public final class TechnomClientFx {

    private static final int LIFE_TICKS = 30;
    private static final int SEGMENTS = 10;
    private static final double HEIGHT = 3.2D;
    private static final double HALF_WIDTH = 0.05D;

    private static final List<Bolt> BOLTS = new ArrayList<>();

    private TechnomClientFx() {
    }

    private static final class Bolt {
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private final int rgb;
        private int life;

        private Bolt(double[] xs, double[] ys, double[] zs, int rgb) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
            this.rgb = rgb;
            this.life = LIFE_TICKS;
        }
    }

    public static void init(IEventBus modBus) {
        // Render and tick events live on the Forge bus, not the mod bus.
        MinecraftForge.EVENT_BUS.addListener(TechnomClientFx::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(TechnomClientFx::onClientTick);
    }

    /** Called on the client when the server reports an effect. */
    public static void accept(byte kind, BlockPos pos, int rgb) {
        if (kind != TechnomFxPacket.KIND_NODE) {
            return;
        }
        if (BOLTS.size() > 32) {
            BOLTS.clear();
        }
        for (int corner = 0; corner < 4; corner++) {
            BOLTS.add(build(pos, corner, rgb));
        }
    }

    /** How many bolts are currently queued to draw; the client probe asserts on this. */
    public static int activeBolts() {
        return BOLTS.size();
    }

    /** Drops everything queued; the client probe clears between cases. */
    public static void clear() {
        BOLTS.clear();
    }

    /**
     * One corner bolt: a jagged line climbing off a corner of the block, seeded from the position
     * so it is the same shape on every client and the same shape if the packet is ever repeated.
     */
    private static Bolt build(BlockPos pos, int corner, int rgb) {
        double baseX = pos.getX() + (corner == 0 || corner == 3 ? 0.15D : 0.85D);
        double baseZ = pos.getZ() + (corner < 2 ? 0.15D : 0.85D);
        long seed = (long) pos.getX() * 3129871L + (long) pos.getY() * 571L
                + (long) pos.getZ() * 9410839L + corner * 7717L;
        double[] xs = new double[SEGMENTS + 1];
        double[] ys = new double[SEGMENTS + 1];
        double[] zs = new double[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double jx = (((seed >>> 33) & 0x3FF) / 1023.0D - 0.5D) * 0.45D;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double jz = (((seed >>> 33) & 0x3FF) / 1023.0D - 0.5D) * 0.45D;
            // The first and last point stay on the axis so the bolt is anchored at both ends.
            double taper = i == 0 || i == SEGMENTS ? 0.0D : 1.0D;
            xs[i] = baseX + jx * taper;
            ys[i] = pos.getY() + 0.1D + (i / (double) SEGMENTS) * HEIGHT;
            zs[i] = baseZ + jz * taper;
        }
        return new Bolt(xs, ys, zs, rgb);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            BOLTS.clear();
            return;
        }
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            if (--it.next().life <= 0) {
                it.remove();
            }
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (BOLTS.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }
        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f mat = pose.last().pose();
        Vec3 cam = camera.getPosition();

        for (Bolt bolt : BOLTS) {
            float fade = bolt.life / (float) LIFE_TICKS;
            int alpha = (int) (fade * 220);
            int red = (bolt.rgb >> 16) & 0xFF;
            int green = (bolt.rgb >> 8) & 0xFF;
            int blue = bolt.rgb & 0xFF;
            for (int i = 0; i + 1 < bolt.xs.length; i++) {
                Vec3 from = new Vec3(bolt.xs[i], bolt.ys[i], bolt.zs[i]);
                Vec3 to = new Vec3(bolt.xs[i + 1], bolt.ys[i + 1], bolt.zs[i + 1]);
                Vec3 along = to.subtract(from);
                if (along.lengthSqr() < 1.0e-8D) {
                    continue;
                }
                Vec3 toCamera = from.add(to).scale(0.5D).subtract(cam);
                Vec3 across = along.cross(toCamera);
                if (across.lengthSqr() < 1.0e-8D) {
                    continue;
                }
                across = across.normalize().scale(HALF_WIDTH);
                Vec3 near = from.add(across);
                Vec3 far = from.subtract(across);
                Vec3 endNear = to.add(across);
                Vec3 endFar = to.subtract(across);
                vertex(consumer, mat, near, red, green, blue, alpha);
                vertex(consumer, mat, far, red, green, blue, alpha);
                vertex(consumer, mat, endFar, red, green, blue, alpha);
                vertex(consumer, mat, endNear, red, green, blue, alpha);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void vertex(VertexConsumer consumer, Matrix4f mat, Vec3 at, int r, int g, int b, int a) {
        consumer.vertex(mat, (float) at.x, (float) at.y, (float) at.z).color(r, g, b, a).endVertex();
    }
}
