package theflogat.technomancy.common.items;

import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.FocusIdentityItem;
import dev.tc4port.thaumcraft.api.focus.FocusStateView;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import theflogat.technomancy.Technomancy;

/**
 * The fusion focus item: identity, upgrade slots and persistent properties of one focus.
 *
 * <p>Ported from {@code ItemFusionFocus}. What the original kept in three fields on the
 * {@link Item} singleton - the picked-up node's aspects, type and modifier - is per carrier here,
 * and not even on this stack: once installed, the link lives in the wand's focus properties
 * ({@link FocusApi#updateInstalledProperties}), so two players, or one player's two wands, never
 * share a selection. That shared state was the recorded defect: a second right-click made another
 * copy of the same node, for everyone at once.</p>
 */
public class FusionFocusItem extends Item implements FocusIdentityItem {

    /** Also the path of the focus profile file, which TC4R requires to match the id. */
    public static final FocusId FOCUS_ID =
            FocusId.of(new ResourceLocation(Technomancy.MOD_ID, "fusion_focus"));

    private static final String TAG_ROOT = "technom_focus";
    private static final String TAG_UPGRADES = "upgrades";
    private static final String TAG_PROPERTIES = "properties";

    public FusionFocusItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public FocusId focusId(ItemStack stack) {
        return FOCUS_ID;
    }

    @Override
    public FocusStateView focusState(ItemStack stack) {
        CompoundTag root = stack.getTagElement(TAG_ROOT);
        if (root == null) {
            return new FocusStateView(FOCUS_ID, List.of(), Map.of());
        }
        List<FocusUpgradeId> upgrades = new ArrayList<>();
        ListTag stored = root.getList(TAG_UPGRADES, Tag.TAG_STRING);
        for (int index = 0; index < stored.size(); index++) {
            FocusUpgradeId upgrade = upgradeId(stored.getString(index));
            if (upgrade != null) {
                upgrades.add(upgrade);
            }
        }
        LinkedHashMap<ResourceLocation, String> properties = new LinkedHashMap<>();
        CompoundTag propertyTag = root.getCompound(TAG_PROPERTIES);
        for (String key : propertyTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                properties.put(id, propertyTag.getString(key));
            }
        }
        return new FocusStateView(FOCUS_ID, upgrades, properties);
    }

    /**
     * Writes the state back into this stack's own tag.
     *
     * <p>Required rather than optional: TC4R rejects a carrier that does not persist exactly the
     * state it was handed, and the default implementation of this method refuses any state with
     * properties at all.</p>
     */
    @Override
    public ItemStack withFocusState(ItemStack stack, FocusStateView state) {
        if (!FOCUS_ID.equals(state.focusId())) {
            throw new IllegalArgumentException("Focus identity cannot change");
        }
        ItemStack result = stack.copyWithCount(1);
        CompoundTag root = new CompoundTag();
        ListTag upgrades = new ListTag();
        state.upgradeSlots().forEach(upgrade -> upgrades.add(net.minecraft.nbt.StringTag.valueOf(upgrade.toString())));
        if (!upgrades.isEmpty()) {
            root.put(TAG_UPGRADES, upgrades);
        }
        if (!state.properties().isEmpty()) {
            CompoundTag properties = new CompoundTag();
            state.properties().forEach((key, value) -> properties.putString(key.toString(), value));
            root.put(TAG_PROPERTIES, properties);
        }
        if (root.isEmpty()) {
            result.removeTagKey(TAG_ROOT);
        } else {
            result.getOrCreateTag().put(TAG_ROOT, root);
        }
        return result;
    }

    @Nullable
    private static FocusUpgradeId upgradeId(String value) {
        try {
            return FocusUpgradeId.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Installs the focus into a wand held in the other hand, which is how every TC4R focus is
     * fitted; there is no separate assembly step.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack focus = player.getItemInHand(hand);
        InteractionHand wandHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!dev.tc4port.thaumcraft.api.wand.WandApi.view(player.getItemInHand(wandHand)).isPresent()) {
            return InteractionResultHolder.pass(focus);
        }
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
            return FocusApi.install(server, wandHand, focus, dev.tc4port.thaumcraft.api.aspect.VisAction.EXECUTE)
                    == dev.tc4port.thaumcraft.api.focus.FocusInstallResult.INSTALLED
                    ? InteractionResultHolder.consume(focus)
                    : InteractionResultHolder.fail(focus);
        }
        return InteractionResultHolder.sidedSuccess(focus, level.isClientSide);
    }

    /** TC4R writes the Vis cost and the upgrade lines; the two usage lines are the port's. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        FocusApi.appendFocusTooltip(stack, lines);
        lines.add(Component.translatable("item.technom.fusion_focus.usage")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.technom.fusion_focus.carry")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
