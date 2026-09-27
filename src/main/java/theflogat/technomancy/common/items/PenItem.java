package theflogat.technomancy.common.items;

import dev.tc4port.thaumcraft.api.item.ScribeTools;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The long-lasting pen: a Thaumcraft scribing tool with thirty times the ink of the vanilla
 * inkwell, and the same two-table research-table formation gesture.
 *
 * <p>{@code ItemPen} implemented {@code IScribeTools} with 3000 durability against TC4's 500 quill
 * (TC4R ships 100), so the ink model is inherited from {@link ScribeTools} unchanged - durability
 * is ink, one point per use, and the pen is repairable at an anvil like any other tool.</p>
 *
 * <p>Version-bound bridge: forming the table uses {@code ResearchTableBlock.form} and
 * {@code CompoundBlueprintCatalog.isTrigger}, both public but in TC4R implementation packages,
 * because {@code api/research/ResearchTableApi} only exposes note transactions and not the
 * formation of the block pair (engineering guide 9.3). The 1.7.10 pen instead swapped two
 * {@code TileTable}s for a {@code TileResearchTable} by hand, which is the same operation against
 * a private structure; going through TC4R's own method is what keeps the block state, the partner
 * orientation and the scribing slot consistent with a table built by the vanilla quill.</p>
 */
public class PenItem extends Item implements ScribeTools {

    /** {@code setMaxDamage(3000)}. */
    public static final int INK_USES = 3000;
    /** {@code ItemPen.onItemUse} walked {@code ForgeDirection} 2..5, i.e. the four horizontals. */
    private static final Direction[] SEARCH_ORDER =
            {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public PenItem(Properties properties) {
        super(properties.stacksTo(1).durability(INK_USES));
    }

    /**
     * Turns a pair of adjacent Thaumcraft tables into a research table and puts the pen in its
     * scribing slot, exactly as the vanilla scribing tools do.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!dev.tc4port.thaumcraft.common.CompoundBlueprintCatalog.isTrigger(
                dev.tc4port.thaumcraft.common.CompoundBlueprintCatalog.RESEARCH_TABLE, level.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        Direction partner = null;
        for (Direction direction : SEARCH_ORDER) {
            if (dev.tc4port.thaumcraft.common.CompoundBlueprintCatalog.isTrigger(
                    dev.tc4port.thaumcraft.common.CompoundBlueprintCatalog.RESEARCH_TABLE,
                    level.getBlockState(pos.relative(direction)))) {
                partner = direction;
                break;
            }
        }
        if (partner == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            dev.tc4port.thaumcraft.block.ResearchTableBlock.form(level, pos, partner, context.getItemInHand().copy());
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        Player player = context.getPlayer();
        if (player != null) {
            player.swing(context.getHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.technom.pen.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
