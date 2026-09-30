package theflogat.technomancy.mixin;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.forge.platform.FluidHelper;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds the {@code getTooltip(ITooltipBuilder, FluidStack, TooltipFlag)} overload back to JEI's
 * {@code FluidHelper} so that GTCEu 7.5.3's {@code gtceu.mixins.json:jei.FluidHelperMixin} has a
 * target to inject into.
 *
 * <p>GTCEu 7.5.3 was built against JEI 15.20 and its mixin hard-codes that overload in its
 * {@code @Inject(method = ...)} selector. JEI dropped the overload at 15.40.0.176 and reverted to
 * {@code getTooltip(List<Component>, FluidStack, TooltipFlag)}, so on any newer JEI the selector
 * matches the surviving {@code getTooltip} by name, the handler descriptor is then rejected, and
 * the failure is fatal: {@code InvalidInjectionException: Invalid descriptor ... Expected
 * (Ljava/util/List;...)V but found (Lmezz/jei/api/gui/builder/ITooltipBuilder;...)V}, thrown while
 * {@code mezz.jei.forge.platform.PlatformHelper} is being constructed. {@code require = 0} does not
 * help: it only covers "the selector matched nothing". GTCEu declares
 * {@code injectors.defaultRequire = 1} as well.
 *
 * <p>The method body is deliberately empty. JEI 15.56 has no {@code ITooltipBuilder} overload
 * anywhere in its API ({@code IPlatformFluidHelperInternal} declares only the {@code List} form),
 * so nothing ever calls this method; it exists purely to satisfy the selector. That also means
 * GTCEu's JEI fluid tooltips stay inert on JEI >= 15.40, which is the same trade-off every other
 * pack makes to get the two mods to load together.
 *
 * <p>{@code priority = 900} (below Mixin's default of 1000) makes this apply before GTCEu's mixin.
 * A pack that already carries an equivalent shim - {@code pollution.mixins.json} has the same
 * mixin at the same priority - just wins the race; the loser is reported as a "Method overwrite
 * conflict ... Skipping method" warning and the outcome is identical, because both bodies are
 * empty.
 */
@Mixin(value = FluidHelper.class, priority = 900)
public abstract class GtceuJeiFluidHelperMixin {

    public void getTooltip(ITooltipBuilder tooltipBuilder, FluidStack fluidStack, TooltipFlag tooltipFlag) {
    }
}
