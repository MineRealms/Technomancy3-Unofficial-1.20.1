package theflogat.technomancy.common.rituals;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.blocks.technom.CatalystBlock;
import theflogat.technomancy.common.player.Affinity;
import theflogat.technomancy.common.player.PlayerAffinity;

/**
 * One ritual: a frame of crystal pillars around a catalyst, and an effect the catalyst runs
 * once the ring matches ({@code Ritual}).
 *
 * <p>The 1.7.10 original keyed everything on block metadata: {@code isCoreComplete} compared
 * {@code w.getBlockMetadata == core.id} and {@code RitualHelper.checkForT} compared crystal
 * metadata. Both are blockstate properties here, but the numbers are the same 0..4 ordering as
 * {@code ItemCrystal.types} — nature, fire, water, light, dark — which is also the order of
 * {@link Type}.</p>
 *
 * <p>The frame is three tiers of four corner pillars; {@link RitualFrames} owns the geometry so
 * the check and the teardown cannot disagree. Applying the effect is the subclass's job; this
 * class only proves the ring and grants the affinity the original granted.</p>
 */
public abstract class Ritual {

    /** {@code Ritual.Type}: the five crystal/catalyst kinds. */
    public enum Type {
        EARTH(0), FIRE(1), WATER(2), LIGHT(3), DARK(4);

        private final int id;

        Type(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public Affinity affinity() {
            return Affinity.byId(id);
        }
    }

    /** Frame type per tier, or {@code null} for "this tier must be empty". */
    private final Type[] frame;
    private final Type core;

    protected Ritual(Type[] frame, Type core) {
        this.frame = new Type[] {null, null, null};
        for (int i = 0; i < Math.min(frame.length, this.frame.length); i++) {
            this.frame[i] = frame[i];
        }
        this.core = core;
    }

    public Type core() {
        return core;
    }

    @Nullable
    public Type frame(int tier) {
        return frame[tier];
    }

    /** The catalyst under this ritual is the right kind, i.e. the core is complete. */
    public boolean isCoreComplete(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CatalystBlock catalyst
                && catalyst.type() == core;
    }

    /** Every tier is either the required crystal ring or, for a {@code null} tier, empty. */
    public boolean isFrameComplete(Level level, BlockPos pos) {
        for (int tier = 0; tier < frame.length; tier++) {
            if (frame[tier] == null) {
                if (!RitualFrames.isTierEmpty(level, pos, tier)) {
                    return false;
                }
            } else if (!RitualFrames.matches(level, pos, tier, frame[tier])) {
                return false;
            }
        }
        return true;
    }

    /** Takes the ring down; used by the rituals that consume their frame. */
    protected void removeFrame(Level level, BlockPos pos) {
        for (int tier = 0; tier < frame.length; tier++) {
            if (frame[tier] != null) {
                RitualFrames.remove(level, pos, tier);
            }
        }
    }

    /**
     * The original's affinity grant: five points of the core's affinity, one of each frame
     * type, and {@code 25 * (number of frame types)} rolls at the player's existence level.
     * The player is looked up by name because that is who the catalyst remembered.
     */
    public void addAffinity(ServerLevel level, ServerPlayer player) {
        PlayerAffinity.add(level, player, core.affinity(), 5);
        for (Type type : frame) {
            if (type != null) {
                PlayerAffinity.add(level, player, type.affinity(), 1);
            }
        }
        int frameTypes = 0;
        for (Type type : frame) {
            if (type != null) {
                frameTypes++;
            }
        }
        for (int i = 0; i <= 25 * frameTypes; i++) {
            PlayerAffinity.addExistencePower(level.getRandom(), player);
        }
    }

    /** Whether the effect can run right now; the catalyst asks before applying. */
    public abstract boolean canApplyEffect(Level level, BlockPos pos);

    /** Applies the effect once. The catalyst calls this on the server thread. */
    public abstract void applyEffect(Level level, BlockPos pos);

    public static List<Type> allTypes() {
        return List.of(Type.values());
    }
}
