package theflogat.technomancy;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Entry point for the modern port. Gameplay modules are added in later milestones. */
@Mod(Technomancy.MOD_ID)
public final class Technomancy {
    public static final String MOD_ID = "technom";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Technomancy() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Technomancy development scaffold initialized; gameplay modules are not yet ported.");
    }
}
