package theflogat.technomancy.common.nodes;

import dev.tc4port.thaumcraft.api.Registration;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusPropertyUpdateResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusActivation;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastAnimation;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import dev.tc4port.thaumcraft.api.node.NodeStateChangeResult;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.FusionFocusItem;
import theflogat.technomancy.common.rituals.RitualFx;

/**
 * The fusion focus in play: select a node, then fuse it into a second one.
 *
 * <p>Two clicks, one link. The first use on an aura node or a node jar remembers it on the wand's
 * focus properties and costs nothing, exactly as the original's free left-click pick-up did. The
 * second use, on a different node, moves the first node's Vis into it in a single
 * {@link NodeApi#replaceLoadedStates} transaction and charges the focus cost; when the source has
 * nothing left, its block is taken away.</p>
 *
 * <p>The original's other gesture is back too, as two explicit halves: a shift-right-click takes a
 * whole node into the focus and removes its block, and a right-click on free space builds it
 * again, Vis and all. The node lives either in the world or inside the stack that is being used —
 * never both — and the focus refuses to absorb a second node while it is already carrying one.
 * Creating a node needs no Mixin: {@link NodeCreation} reuses the path TC4R's own
 * {@code AuraNodeBlock.setPlacedBy} takes.</p>
 *
 * <p>The transaction boundary is still the two-node compare-and-set TC4R documents for fusing -
 * composing two single-node calls could duplicate or lose Vis if the second one failed.</p>
 */
public final class FusionFocusAction {

    public static final ResourceLocation ACTION_ID = new ResourceLocation(Technomancy.MOD_ID, "fuse_node");
    /** Focus property that holds the selected source node. */
    public static final ResourceLocation LINK_PROPERTY = new ResourceLocation(Technomancy.MOD_ID, "fusion_link");
    /** Focus property that holds a node the focus has taken into itself. */
    public static final ResourceLocation CARRY_PROPERTY = new ResourceLocation(Technomancy.MOD_ID, "fusion_carry");
    /** Research the action is gated behind; the same key the focus recipe uses. */
    public static final ResearchKey RESEARCH = ResearchKey.parse("technom:FUSIONFOCUS");
    /** Blocks away a node may be selected or fused, bounded by the focus action API. */
    public static final int RANGE = 12;

    @Nullable
    private static Registration registration;

    private FusionFocusAction() {
    }

    /** Called once from common setup; a second call is a no-op. */
    public static synchronized void register() {
        if (registration != null) {
            return;
        }
        registration = FocusActionApi.register(
                new FocusActionSpec(ACTION_ID, new FocusActionTarget.Focus(FusionFocusItem.FOCUS_ID),
                        Set.of(FocusActivation.USE_BLOCK), ResearchRequirement.required(RESEARCH), RANGE, 0,
                        FocusCastAnimation.INSTANT, ActionPresentationAudience.NONE),
                FusionFocusAction::handle);
    }

    /**
     * One dispatch of the focus.
     *
     * <p>Both phases walk the same branches and call {@code pay} with the same cost in the same
     * order, which is what the action contract requires: the Vis is debited between them, and a
     * simulation that says "this works" has to be the phase that spends nothing.</p>
     */
    static FocusActionResult handle(FocusActionContext context, VisAction action) {
        if (!(context.authoritativeHit() instanceof BlockHitResult hit)
                || context.authoritativeHit().getType() != HitResult.Type.BLOCK) {
            return FocusActionResult.PASS;
        }
        ServerLevel level = context.player().serverLevel();
        BlockPos target = hit.getBlockPos();
        AuraNodeState targetState = nodeState(level, target);
        AuraNodeState carried = carried(context);
        // With a node inside the focus, a click on free space puts it back down. That is the
        // original's "absorb a node, then build it elsewhere" gesture, split in two so neither
        // half can lose a node: the node exists either in the world or in the focus, never both.
        if (carried != null && targetState == null) {
            return place(context, action, level, target, carried);
        }
        if (targetState == null) {
            return FocusActionResult.PASS;
        }
        if (carried == null && context.player().isShiftKeyDown()) {
            return absorb(context, action, level, target, targetState);
        }
        Link link = Link.parse(context.installedFocus().properties().get(LINK_PROPERTY));
        if (link == null || link.position().equals(target) || !link.dimension().equals(dimension(level))) {
            return select(context, action, target, targetState);
        }
        return fuse(context, action, level, link, target, targetState);
    }

    /** Remembers the node under the cursor. Free, and it replaces any earlier selection. */
    private static FocusActionResult select(FocusActionContext context, VisAction action, BlockPos target,
            AuraNodeState state) {
        if (action == VisAction.SIMULATE) {
            return FocusActionResult.SUCCESS;
        }
        Link link = new Link(dimension(context.player().serverLevel()), target, state.instanceId());
        FocusPropertyUpdateResult result = FocusApi.updateInstalledProperties(context.player(), context.hand(),
                java.util.Map.of(LINK_PROPERTY, link.encode()), Set.of(), VisAction.EXECUTE);
        if (result != FocusPropertyUpdateResult.UPDATED && result != FocusPropertyUpdateResult.UNCHANGED) {
            return FocusActionResult.FAILED;
        }
        ServerLevel level = context.player().serverLevel();
        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        return FocusActionResult.SUCCESS;
    }

    /**
     * Moves the selected node's Vis into the node under the cursor.
     *
     * <p>Both states are read fresh in this phase and submitted as the compare-and-set witnesses,
     * so a node that changed since it was selected simply makes the fusion fail with nothing
     * spent.</p>
     */
    private static FocusActionResult fuse(FocusActionContext context, VisAction action, ServerLevel level,
            Link link, BlockPos target, AuraNodeState targetState) {
        AuraNodeState sourceState = nodeState(level, link.position());
        if (sourceState == null || !sourceState.instanceId().equals(link.instanceId())) {
            // The selection is stale: forget it and treat this click as a new selection instead.
            return select(context, action, target, targetState);
        }
        NodeFusion.Result fusion = NodeFusion.fuse(sourceState, targetState);
        if (!fusion.moved()) {
            return FocusActionResult.FAILED;
        }
        if (!context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        NodeStateChangeResult result = NodeApi.replaceLoadedStates(level,
                link.position(), sourceState, fusion.source(),
                target, targetState, fusion.destination(), action);
        if (action == VisAction.SIMULATE) {
            return result == NodeStateChangeResult.WOULD_REPLACE
                    ? FocusActionResult.SUCCESS : FocusActionResult.FAILED;
        }
        if (result != NodeStateChangeResult.REPLACED) {
            return FocusActionResult.FAILED;
        }
        if (fusion.sourceEmptied()) {
            // Only an exhausted husk is removed, and only after its Vis is safely in the
            // destination: the block goes away, nothing is created anywhere.
            level.removeBlock(link.position(), false);
        }
        FocusApi.updateInstalledProperties(context.player(), context.hand(), java.util.Map.of(),
                Set.of(LINK_PROPERTY), VisAction.EXECUTE);
        context.present(Vec3.atCenterOf(link.position()), Vec3.atCenterOf(target), null, 1.0D,
                fusion.movedVis(), ActionPresentationAudience.NONE);
        level.playSound(null, target, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        Technomancy.LOGGER.debug("Fusion focus moved {} base and {} vis from {} into {}",
                fusion.movedBase(), fusion.movedVis(), link.position(), target);
        return FocusActionResult.SUCCESS;
    }

    /**
     * Takes a whole node into the focus and removes its block.
     *
     * <p>The original did this on a plain right-click and left the node sitting in an item
     * singleton, which is the bug the two-click link above was written to avoid. It is a
     * shift-right-click here, it stores the node in the focus properties of the stack being used,
     * and it refuses to run while the focus is already carrying one — so a node is never
     * overwritten and never duplicated. Free, like the selection.</p>
     */
    private static FocusActionResult absorb(FocusActionContext context, VisAction action, ServerLevel level,
            BlockPos target, AuraNodeState state) {
        if (action == VisAction.SIMULATE) {
            return FocusActionResult.SUCCESS;
        }
        String encoded = encode(state);
        if (encoded == null) {
            return FocusActionResult.FAILED;
        }
        if (!write(context, java.util.Map.of(CARRY_PROPERTY, encoded,
                LINK_PROPERTY, new Link(dimension(level), target, state.instanceId()).encode()), Set.of())) {
            return FocusActionResult.FAILED;
        }
        if (!level.removeBlock(target, false)) {
            // The block would not come away, so the focus must not go on claiming to carry it.
            write(context, java.util.Map.of(), Set.of(CARRY_PROPERTY, LINK_PROPERTY));
            return FocusActionResult.FAILED;
        }
        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.0F);
        Technomancy.LOGGER.debug("Fusion focus absorbed the node at {}", target);
        return FocusActionResult.SUCCESS;
    }

    /** Puts a carried node back down. Costs what a fusion costs; the node's Vis is not topped up. */
    private static FocusActionResult place(FocusActionContext context, VisAction action, ServerLevel level,
            BlockPos target, AuraNodeState carried) {
        boolean free = level.isLoaded(target)
                && (level.getBlockState(target).isAir() || level.getBlockState(target).canBeReplaced());
        if (!free || !context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.SIMULATE) {
            return FocusActionResult.SUCCESS;
        }
        AspectId dominant = carried.currentVis().amounts().isEmpty() ? null
                : carried.currentVis().amounts().keySet().iterator().next();
        if (!NodeCreation.create(level, target, carried.type(), carried.modifier(),
                carried.baseVis(), carried.currentVis())) {
            return FocusActionResult.FAILED;
        }
        RitualFx.nodeCreated(level, target, RitualFx.aspectColour(dominant));
        if (!write(context, java.util.Map.of(), Set.of(CARRY_PROPERTY))) {
            // The stack still says it is carrying this node, so leaving the new one standing
            // would be the same node in two places. Take the world change back instead.
            level.removeBlock(target, false);
            return FocusActionResult.FAILED;
        }
        level.playSound(null, target, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        Technomancy.LOGGER.debug("Fusion focus rebuilt a node at {}", target);
        return FocusActionResult.SUCCESS;
    }

    private static boolean write(FocusActionContext context, java.util.Map<ResourceLocation, String> values,
            Set<ResourceLocation> removed) {
        FocusPropertyUpdateResult result = FocusApi.updateInstalledProperties(context.player(), context.hand(),
                values, removed, VisAction.EXECUTE);
        return result == FocusPropertyUpdateResult.UPDATED || result == FocusPropertyUpdateResult.UNCHANGED;
    }

    @Nullable
    private static AuraNodeState carried(FocusActionContext context) {
        String value = context.installedFocus().properties().get(CARRY_PROPERTY);
        if (value == null) {
            return null;
        }
        return AuraNodeState.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString(value))
                .result()
                .orElse(null);
    }

    @Nullable
    private static String encode(AuraNodeState state) {
        return AuraNodeState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state)
                .result()
                .map(com.google.gson.JsonElement::toString)
                .orElse(null);
    }

    @Nullable
    private static AuraNodeState nodeState(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AuraNodeView node)) {
            return null;
        }
        return node.nodeState();
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /**
     * The selected node, as it is stored in the focus property: dimension, position and the node's
     * persistent scan identity, so a node that was replaced in the meantime is not mistaken for
     * the one that was selected.
     */
    public record Link(String dimension, BlockPos position, Optional<UUID> instanceId) {

        public String encode() {
            return dimension + ";" + position.getX() + "," + position.getY() + "," + position.getZ()
                    + ";" + instanceId.map(UUID::toString).orElse("");
        }

        @Nullable
        public static Link parse(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String[] parts = value.split(";", -1);
            if (parts.length != 3) {
                return null;
            }
            String[] coordinates = parts[1].split(",", -1);
            if (coordinates.length != 3) {
                return null;
            }
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2]));
                Optional<UUID> id = parts[2].isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(parts[2]));
                return new Link(parts[0], pos, id);
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
