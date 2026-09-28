package theflogat.technomancy.common.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import theflogat.technomancy.common.network.TechnomNetwork;

/**
 * Server tick hook for the two things the player's Existence drives: a passive effect once the
 * affinity is established, and the HUD sync packet when the numbers move.
 *
 * <p>The effect is the modern stand-in for the original's drown/slowFall hooks: an aligned
 * player with enough Existence keeps water breathing (light), fire resistance (fire) or
 * resistance (dark), refreshed every tick so it fades the moment the affinity does.</p>
 */
public final class PlayerAffinityEffects {

    private static final int SYNC_INTERVAL = 20;

    private PlayerAffinityEffects() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        bus.addListener(PlayerAffinityEffects::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Affinity affinity = PlayerAffinity.dominant(player);
        switch (affinity) {
            case LIGHT -> player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false));
            case FIRE -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
            case DARK -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false));
            case EARTH -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
            case WATER -> player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
            default -> { }
        }
        if (player.tickCount % SYNC_INTERVAL == 0) {
            TechnomNetwork.sendExistence(player, PlayerAffinity.existenceLevel(player),
                    PlayerAffinity.existencePower(player), affinity.id());
        }
    }
}
