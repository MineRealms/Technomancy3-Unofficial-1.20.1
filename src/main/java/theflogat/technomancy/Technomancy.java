package theflogat.technomancy;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import theflogat.technomancy.common.energy.EnergyUnits;
import theflogat.technomancy.common.machines.CondenserBalance;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelLoader;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.registry.TechnomBlocks;
import theflogat.technomancy.common.registry.TechnomCreativeTabs;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.compat.gtceu.GtceuEnergyIntegration;
import theflogat.technomancy.compat.gtceu.GtceuPresence;
import theflogat.technomancy.config.TechnomancyConfig;

/** Entry point of the modern port. */
@Mod(Technomancy.MOD_ID)
public final class Technomancy {
    public static final String MOD_ID = "technom";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Forge 47.4.23 injects the loading context: {@code FMLModContainer.constructMod()} looks up
     * {@code getDeclaredConstructor(FMLJavaModLoadingContext.class)} first and only falls back to
     * the no-arg constructor. Both {@code FMLJavaModLoadingContext.get()} and
     * {@code ModLoadingContext.get()} are {@code @Deprecated(forRemoval = true, since = "1.21.1")}
     * in this version, so the injected instance is the supported form. Its inherited
     * {@code registerConfig} resolves the owner through the overridden {@code getContainer()},
     * not through the thread-local, so no static accessor is needed.
     */
    public Technomancy(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, TechnomancyConfig.SPEC);

        // Blocks before items: the block registry populates the item registry with its
        // BlockItems, and the creative tab enumerates the item registry.
        TechnomBlocks.BLOCKS.register(modBus);
        TechnomItems.ITEMS.register(modBus);
        TechnomBlockEntities.TYPES.register(modBus);
        TechnomCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        // Server-side data: the aspect fuel table is a data pack, so it reloads with /reload.
        MinecraftForge.EVENT_BUS.addListener(EssentiaFuelLoader::onAddReloadListener);
        // S2 nodes, wands and fusion: wand charging (inventory pass + technoturge FE capability).
        theflogat.technomancy.common.wands.WandChargeEvents.register(MinecraftForge.EVENT_BUS);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> theflogat.technomancy.client.TechnomancyClient.init(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        long rate = EnergyUnits.freeze(TechnomancyConfig.Q_PER_EU.get());
        LOGGER.info("Technomancy energy rate fixed at {} FE per EU for this session", rate);
        checkCondenserBalance();
        // S2 nodes, wands and fusion: the fusion focus action must be registered before any
        // player can dispatch it, and the registry is keyed on the focus id, not on the item.
        event.enqueueWork(theflogat.technomancy.common.nodes.FusionFocusAction::register);
        // Gated so no GTCEu class is resolved in a game without it; the isolated bootstrap is the
        // first class here that may touch the GT API.
        if (GtceuPresence.isLoaded()) {
            GtceuEnergyIntegration.install(rate);
        }
    }

    /**
     * Startup check for the one rule the condenser and the dynamo share: making a unit of
     * potentia must cost more than burning it gives back, or the two blocks wired together are
     * free energy. It reports rather than throws, because a player who mistunes a config value
     * should get a loud, actionable log line instead of a game that will not start; the shipped
     * defaults are held to the rule by {@code CondenserBalanceTest}.
     */
    private static void checkCondenserBalance() {
        long cost = TechnomancyConfig.CONDENSER_COST.get();
        double scale = TechnomancyConfig.ESSENTIA_FUEL_SCALE.get();
        String message = CondenserBalance.describe(cost, scale);
        switch (CondenserBalance.verdict(cost, scale)) {
            case PERPETUAL_MOTION -> LOGGER.error(message);
            case NARROW -> LOGGER.warn(message);
            case SAFE -> LOGGER.info(message);
        }
    }
}
