package theflogat.technomancy;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.config.TechnomancyConfig;

/** Entry point of the modern port. */
@Mod(Technomancy.MOD_ID)
public final class Technomancy {
    public static final String MOD_ID = "technom";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Technomancy() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TechnomancyConfig.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        long rate = EnergyUnits.freeze(TechnomancyConfig.Q_PER_EU.get());
        LOGGER.info("Technomancy energy rate fixed at {} FE per EU for this session", rate);
    }
}
