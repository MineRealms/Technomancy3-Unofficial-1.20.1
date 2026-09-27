package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusStateView;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.items.FusionFocusItem;
import theflogat.technomancy.common.nodes.FusionFocusAction;
import theflogat.technomancy.common.registry.TechnomItems;

/**
 * The fusion focus as the game sees it: a registered focus with a loaded profile, a registered
 * action, and per-stack state that two carriers never share. Written for the merged final run;
 * not yet executed.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FusionFocusGameTests {

    private static final String BATCH = "technom_fusion";
    private static final AspectId AER = AspectId.parse("aer");

    private FusionFocusGameTests() {}

    /** The profile is a data file whose name must equal the focus id; a typo disables the focus. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theFocusProfileAndActionAreRegistered(GameTestHelper helper) {
        helper.assertTrue(FocusApi.registry().profile(FusionFocusItem.FOCUS_ID).isPresent(),
                "no focus profile for " + FusionFocusItem.FOCUS_ID
                        + "; check data/technom/thaumcraft/focus_catalog/fusion_focus.json");
        var cost = FocusApi.registry().profile(FusionFocusItem.FOCUS_ID).orElseThrow()
                .fixedCost(Map.of()).orElseThrow(() -> new AssertionError("the fusion focus has no fixed cost"));
        helper.assertTrue(cost.amounts().size() == 6, "the cost should name all six primals: " + cost.amounts());
        cost.amounts().forEach((channel, amount) -> helper.assertTrue(amount == 3000,
                channel + " costs " + amount + " centivis instead of 3000"));

        boolean registered = FocusActionApi.registry().actions().stream()
                .anyMatch(spec -> spec.actionId().equals(FusionFocusAction.ACTION_ID));
        helper.assertTrue(registered, "the fusion action is not registered; common setup never ran it");
        helper.succeed();
    }

    /** Focus state lives on the stack, never on the item: the 1.7.10 shared-state defect. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void twoFociDoNotShareTheirSelection(GameTestHelper helper) {
        ItemStack first = new ItemStack(TechnomItems.FUSION_FOCUS.get());
        ItemStack second = new ItemStack(TechnomItems.FUSION_FOCUS.get());
        FusionFocusItem item = (FusionFocusItem) first.getItem();
        ResourceLocation key = FusionFocusAction.LINK_PROPERTY;

        ItemStack linked = item.withFocusState(first,
                new FocusStateView(FusionFocusItem.FOCUS_ID, java.util.List.of(), Map.of(key, "overworld;1,2,3;")));
        helper.assertTrue("overworld;1,2,3;".equals(item.focusState(linked).properties().get(key)),
                "the focus did not persist its own property");
        helper.assertTrue(item.focusState(second).properties().isEmpty(),
                "a second focus picked up the first one's selection");
        helper.assertTrue(item.focusState(first).properties().isEmpty(),
                "withFocusState must not write through to the original stack");
        helper.succeed();
    }

    /** The encoded link survives a round trip, including a node identity. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theLinkEncodingRoundTrips(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        BlockPos pos = new BlockPos(-40, 63, 128);
        FusionFocusAction.Link link = new FusionFocusAction.Link("minecraft:overworld", pos, Optional.of(id));
        FusionFocusAction.Link parsed = FusionFocusAction.Link.parse(link.encode());
        helper.assertTrue(parsed != null && parsed.equals(link), "round trip gave " + parsed);
        helper.assertTrue(FusionFocusAction.Link.parse("nonsense") == null, "a malformed link must not parse");
        helper.assertTrue(link.encode().length() <= FocusApi.MAX_PROPERTY_VALUE_LENGTH,
                "the encoded link is longer than a focus property may be");
        helper.succeed();
    }

    /** A sanity check that the node states used by the fusion are the ones TC4R accepts. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void nodeStatesUsedByTheFusionAreValid(GameTestHelper helper) {
        LinkedHashMap<AspectId, Integer> amounts = new LinkedHashMap<>();
        amounts.put(AER, 8);
        AuraNodeState state = new AuraNodeState(Optional.of(UUID.randomUUID()), NodeTypeId.NORMAL,
                NodeModifierId.NONE, new NodeVis(amounts), new NodeVis(amounts));
        helper.assertTrue(state.currentVis().amount(AER) == 8, "node vis did not survive construction");
        helper.succeed();
    }
}
