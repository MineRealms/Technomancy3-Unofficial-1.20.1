package theflogat.technomancy.common.nodes;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import net.minecraft.core.Direction;

/**
 * The world-free half of {@code TileNodeGenerator.generateNode}: the two dedicated aspects, the
 * node type and modifier the aurum/taint totals select, the Vis the node is born with and the
 * energy one creation spends.
 *
 * <p>Straight from {@code TileNodeGenerator.generateNode} and {@code onWandRightClick}: the
 * fabricator looking north or west sucks auram, the other vitium; both must be over
 * {@value #MIN_TOTAL_ESSENTIA} units together to begin; the ritual runs
 * {@value #RITUAL_TICKS} ticks; the price is {@code round(((aurum + vitium) / 2)^2 * 762.939453125)}
 * RF, and the same half-total is the Vis the new node starts with.</p>
 */
public final class NodeCreationRules {

    /** {@code if (partner != null && amount + partner.amount > 64)}. */
    public static final int MIN_TOTAL_ESSENTIA = 64;
    /** {@code if (step == 200 && initiator)}. */
    public static final int RITUAL_TICKS = 200;
    /** {@code Math.pow((amount + partner.amount) / 2, 2) * 762.939453125}. */
    public static final double ENERGY_FACTOR = 762.939453125D;

    /** {@code Aspect.AURA}. */
    public static final AspectId AURAM = AspectId.parse("auram");
    /** {@code Aspect.TAINT}. */
    public static final AspectId VITIUM = AspectId.parse("vitium");

    private NodeCreationRules() {
    }

    /**
     * {@code if (facing == 2 || facing == 4)}: the north/west fabricator takes auram and the
     * south/east one vitium.
     */
    public static AspectId dedicatedAspect(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.WEST ? AURAM : VITIUM;
    }

    /**
     * {@code MathHelper.round(Math.pow((amount + partner.amount) / 2, 2) * 762.939453125)}; the
     * original divided as integers first, so this does too.
     */
    public static long energyCost(int firstAmount, int secondAmount) {
        long half = (firstAmount + secondAmount) / 2L;
        return Math.round(half * half * ENERGY_FACTOR);
    }

    /** The Vis the created node starts with: {@code (aurum + taint) / 2}. */
    public static int nodeVis(int aurum, int taint) {
        return (aurum + taint) / 2;
    }

    /** {@code generateNode}'s type cascade. */
    public static NodeTypeId type(int aurum, int taint) {
        int sum = aurum + taint;
        if (aurum == taint && (sum == 122 || sum == 152 || sum == 218 || sum == 510)) {
            return NodeTypeId.PURE;
        }
        if (sum > 256) {
            return NodeTypeId.HUNGRY;
        }
        if (aurum - 64 > taint) {
            return NodeTypeId.UNSTABLE;
        }
        if (taint - 64 > aurum) {
            return taint > 96 ? NodeTypeId.TAINTED : NodeTypeId.DARK;
        }
        return NodeTypeId.NORMAL;
    }

    /** {@code generateNode}'s modifier cascade; a missing modifier is {@link NodeModifierId#NONE}. */
    public static NodeModifierId modifier(int aurum, int taint) {
        int sum = aurum + taint;
        if (sum < 80) {
            return NodeModifierId.FADING;
        }
        if ((sum > 200 && sum < 256) || sum == 510) {
            return NodeModifierId.BRIGHT;
        }
        if (sum > 350 && sum != 510) {
            return NodeModifierId.PALE;
        }
        return NodeModifierId.NONE;
    }
}
