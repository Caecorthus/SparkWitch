package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Coordinates the focused services implementing the Wraith lifecycle.
 * 组装实现冤魂生命周期的各个聚焦服务。
 */
public final class WraithService {
    private static boolean registered;

    private WraithService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        WraithTaskService.register();
        WraithPromotionService.register();
        WraithSessionService.register();
        WraithRuntimeService.register();
        WraithPlayerIsolationService.register();
        WraithInteractionService.register();
        WraithDeadPlayerParticipationService.register();
        WraithFactionBridge.register();

        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            WraithRoundQuotaService.beginRound(serverWorld, game.getAllPlayers().size());
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                WraithPlayerComponent component = WraithPlayerComponent.KEY.get(player);
                component.clear();
                if (game.hasAnyRole(player)) {
                    component.captureReturnPoint(player);
                }
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WraithSessionService.clearPlayer(serverPlayer);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                WraithSessionService.clearRoundState(serverWorld);
            }
        });
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        WraithSessionService.clearPlayer(player);
    }

    public static void clearRoundState(ServerWorld world) {
        WraithSessionService.clearRoundState(world);
    }
}
