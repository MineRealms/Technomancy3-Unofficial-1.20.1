package theflogat.technomancy.common.fluids;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import theflogat.technomancy.Technomancy;

/**
 * 1.20.1 counterpart of the original {@code ManaFluid}: the fluid attributes the 1.7.10 {@code Fluid}
 * carried are a Forge {@link FluidType} now, while the source and flowing fluids themselves are
 * plain {@code ForgeFlowingFluid} instances in {@link TechnomFluids}.
 */
public final class ManaFluidType extends FluidType {

    /** {@code technom:textures/block/manafluid_still.png}. */
    public static final ResourceLocation STILL =
            new ResourceLocation(Technomancy.MOD_ID, "block/manafluid_still");
    /** {@code technom:textures/block/manafluid_flow.png}. */
    public static final ResourceLocation FLOWING =
            new ResourceLocation(Technomancy.MOD_ID, "block/manafluid_flow");

    public ManaFluidType() {
        super(FluidType.Properties.create()
                .descriptionId("fluid.technom.mana")
                .density(1000)
                .viscosity(1000)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING;
            }
        });
    }
}
