package theflogat.technomancy.common.tiles.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * The creative jar's transfer rules, free of any world so the TC4R contract bounds can be unit
 * tested directly.
 *
 * <p>{@code configured} is the jar's aspect after the registry check, {@code null} when unset.</p>
 */
public final class CreativeJarContract {

    /** {@code TileCreativeJar.max}; only what inspectors and tubes are shown, never a limit. */
    public static final int DISPLAY_AMOUNT = 320;

    /**
     * Zero, so any taker can drain it. TC4's jar asked 32 (64 labelled) before a tube could adopt
     * its aspect, which made the test source unusable from an unlabelled jar at the end of a
     * tube. Deliberate deviation; this block exists to be drained.
     */
    public static final int MINIMUM_SUCTION = 0;

    private CreativeJarContract() {
    }

    /** TC4's jar connects on its top face only, and so does this one. */
    public static boolean isOutputFace(@Nullable Direction face) {
        return face == Direction.UP;
    }

    /** {@code EssentiaSource}: the whole request or zero. */
    public static int extract(@Nullable AspectId configured, @Nullable AspectId requested, int amount) {
        return amount > 0 && configured != null && configured.equals(requested) ? amount : 0;
    }

    /** {@code EssentiaTransport.takeEssentia}: a value in {@code [0, amount]}. */
    public static int take(@Nullable AspectId configured, @Nullable AspectId requested, int amount,
            @Nullable Direction face) {
        return isOutputFace(face) ? extract(configured, requested, amount) : 0;
    }

    public static int available(@Nullable AspectId configured, @Nullable AspectId requested,
            @Nullable Direction face) {
        return isOutputFace(face) && configured != null && configured.equals(requested) ? DISPLAY_AMOUNT : 0;
    }
}
