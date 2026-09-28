package theflogat.technomancy.common.rituals;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import theflogat.technomancy.common.network.TechnomFxPacket;
import theflogat.technomancy.common.network.TechnomNetwork;

import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * The visual half of a ritual or a node creation: particles for everyone nearby plus a bolt the
 * clients draw, so the two server-side acts a player performs blind today are actually visible.
 *
 * <p>Nothing here is cosmetic-only bookkeeping — the whole point is that the effect has to reach
 * the clients, so every method here takes a {@link ServerLevel} and sends from it. Particles go
 * out through {@code ServerLevel#sendParticles}, which already targets the right players; the
 * bolt is a geometry only a client can draw, so it goes as a packet to the same neighbourhood.</p>
 */
public final class RitualFx {

    private RitualFx() {
    }

    /** Element colour per ritual type; there is no colour anywhere on {@link Ritual.Type} itself. */
    public static int colour(Ritual.Type type) {
        return switch (type) {
            case EARTH -> 0x3CB371;
            case FIRE -> 0xFF6A00;
            case WATER -> 0x3A7BFF;
            case LIGHT -> 0xFFF7B0;
            case DARK -> 0x8A2BE2;
        };
    }

    /** An aspect's own colour, or white when the registry has not answered yet. */
    public static int aspectColour(@Nullable AspectId aspect) {
        if (aspect == null) {
            return 0xFFFFFF;
        }
        return AspectApi.registry().get(aspect).map(AspectDefinition::color).orElse(0xFFFFFF);
    }

    /**
     * A node just came into being: four jagged bolts from the block's corners and a burst, in the
     * aspect's colour.
     */
    public static void nodeCreated(ServerLevel level, BlockPos pos, int rgb) {
        bolts(level, pos, rgb);
        burst(level, pos, rgb, 1.0D);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.7F, 1.2F);
        sendBolt(level, pos, rgb, TechnomFxPacket.KIND_NODE);
    }

    /** A ritual just fired: a ring on the ground and a column above the catalyst. */
    public static void ritualFired(ServerLevel level, BlockPos pos, Ritual.Type type) {
        int rgb = colour(type);
        ring(level, pos, rgb, 2.5D);
        column(level, pos, rgb, 3.0D);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    // ---- shapes ----

    /**
     * The four corner bolts, mirroring {@code TileNodeGenerator.shootLightning}: upstream picked
     * one of four corner-to-outside segments per facing, so this walks all four corners and lets
     * each climb, jittering sideways so it reads as a bolt and not a stick.
     */
    private static void bolts(ServerLevel level, BlockPos pos, int rgb) {
        DustParticleOptions dust = dust(rgb, 1.0F);
        for (int corner = 0; corner < 4; corner++) {
            double cx = pos.getX() + (corner == 0 || corner == 3 ? 0.15D : 0.85D);
            double cz = pos.getZ() + (corner < 2 ? 0.15D : 0.85D);
            double x = cx;
            double y = pos.getY() + 0.1D;
            double z = cz;
            int steps = 12;
            for (int s = 0; s < steps; s++) {
                double nx = cx + (level.getRandom().nextDouble() - 0.5D) * 0.45D;
                double nz = cz + (level.getRandom().nextDouble() - 0.5D) * 0.45D;
                double ny = y + 3.2D / steps;
                level.sendParticles(dust, nx, ny, nz, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nx, ny, nz, 1, 0, 0, 0, 0);
                x = nx;
                y = ny;
                z = nz;
            }
            // cap the bolt so it is not just a fading trail
            level.sendParticles(dust, x, y, z, 3, 0.15D, 0.15D, 0.15D, 0);
        }
    }

    private static void burst(ServerLevel level, BlockPos pos, int rgb, double spread) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(dust(rgb, 1.2F), at.x, at.y, at.z, 24, spread, spread, spread, 0.1D);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 12, spread, spread, spread, 0.05D);
    }

    private static void ring(ServerLevel level, BlockPos pos, int rgb, double radius) {
        DustParticleOptions dust = dust(rgb, 1.0F);
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            double angle = (i / (double) steps) * Math.PI * 2;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double z = pos.getZ() + 0.5 + Mth.sin((float) angle) * radius;
            level.sendParticles(dust, x, pos.getY() + 0.2, z, 1, 0, 0, 0, 0);
        }
    }

    private static void column(ServerLevel level, BlockPos pos, int rgb, double height) {
        DustParticleOptions dust = dust(rgb, 0.9F);
        int steps = 16;
        for (int s = 0; s <= steps; s++) {
            double y = pos.getY() + (s / (double) steps) * height;
            level.sendParticles(dust, pos.getX() + 0.5, y, pos.getZ() + 0.5, 1, 0.05D, 0, 0.05D, 0.02D);
        }
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), scale);
    }

    private static void sendBolt(ServerLevel level, BlockPos pos, int rgb, byte kind) {
        TechnomNetwork.sendFx(level, pos, new TechnomFxPacket(kind, pos, rgb));
    }
}
