package theflogat.technomancy.common.coils;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One remote endpoint a coil draws from: a block position in the coil's own level plus the face
 * the player clicked when making the link.
 *
 * <p>The face matters for items: a sided inventory exposes different slots on different faces,
 * and querying it without a face would bypass its automation rules (engineering guide 6.2). The
 * essentia coil ignores it, because a TC4R {@code EssentiaSource} is drained without a face.</p>
 *
 * <p>No dimension is stored: a link always lives in the level of the coil that owns it, and the
 * coupler refuses to make one across dimensions. Holding only a position - never a block entity -
 * is what lets a link survive its endpoint being unloaded and still be re-validated afterwards.</p>
 */
public record CoilLink(BlockPos pos, @Nullable Direction face) {

    public CoilLink {
        pos = Objects.requireNonNull(pos, "pos").immutable();
    }
}
