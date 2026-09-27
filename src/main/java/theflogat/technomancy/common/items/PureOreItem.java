package theflogat.technomancy.common.items;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import theflogat.technomancy.common.machines.processing.OreProgress;
import theflogat.technomancy.common.machines.processing.ProcessingModule;
import theflogat.technomancy.common.machines.processing.PureOreMaterial;

/**
 * One purity stage of one purified ore ({@code ItemProcessedOre}).
 *
 * <p>The stage is the item's identity rather than a damage value, so a furnace recipe can give a
 * different number of ingots per stage without reading NBT. Every stage of a material shares one
 * display name, as the original's {@code getItemStackDisplayName} did, so the six items do not
 * need six lang keys.</p>
 */
public class PureOreItem extends Item {

    private final PureOreMaterial material;
    private final int stage;

    public PureOreItem(PureOreMaterial material, int stage, Properties properties) {
        super(properties);
        if (stage < 0 || stage > OreProgress.MAX_STAGE) {
            throw new IllegalArgumentException("stage out of range: " + stage);
        }
        this.material = material;
        this.stage = stage;
    }

    public PureOreMaterial material() {
        return material;
    }

    /** Purity stage, 0..{@value OreProgress#MAX_STAGE}. */
    public int stage() {
        return stage;
    }

    /** The pass record carried by this stack, which is all its NBT holds. */
    public static OreProgress progressOf(ItemStack stack) {
        return stack.hasTag() ? OreProgress.load(stack.getOrCreateTag()) : OreProgress.NONE;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(material.translationKey());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("technom.tooltip.purity", stage + 1)
                .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        Map<String, Integer> passes = progressOf(stack).allPasses();
        if (passes.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("technom.tooltip.processed_by")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        passes.forEach((id, count) -> {
            ProcessingModule module = ProcessingModule.byId(id);
            // A record written while a module was installed must still read sensibly without it.
            Component name = module == null ? Component.literal(id) : Component.translatable(module.translationKey());
            int max = module == null ? count : module.maxPasses();
            tooltip.add(Component.translatable("technom.tooltip.passes", name, count, max)
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
