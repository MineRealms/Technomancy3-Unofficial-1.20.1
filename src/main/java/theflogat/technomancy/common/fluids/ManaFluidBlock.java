package theflogat.technomancy.common.fluids;

import java.util.function.Supplier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * {@code BlockManaFluid}: the placed form of the mana fluid. The original was a
 * {@code BlockFluidClassic} that refused to displace other liquids; 1.20.1's {@link LiquidBlock}
 * already leaves liquids alone, so nothing extra is needed beyond the properties.
 */
public final class ManaFluidBlock extends LiquidBlock {

    public ManaFluidBlock(Supplier<? extends FlowingFluid> fluid) {
        super(fluid, BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE)
                .noCollission()
                .strength(100.0F)
                .noLootTable());
    }
}
