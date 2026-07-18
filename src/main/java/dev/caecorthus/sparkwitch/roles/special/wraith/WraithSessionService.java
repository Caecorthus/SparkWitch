package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Owns activation, reconnect validation, cleanup, and terminal transition.
 * 持有激活、重连校验、清理与终止转换。
 */
public final class WraithSessionService {
    private static boolean registered;

    private WraithSessionService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
    }

    static void activatePlayer(ServerPlayerEntity player) {
        WraithGameModeService.activate(player);
        WraithRuntimeStateService.apply(player, true);
        WraithVoiceChannelService.restoreLivingChannel(player);
    }

    static void clearPlayer(ServerPlayerEntity player) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        boolean wasActive = wraith.isActive();
        WraithDeathService.clearPlayer(player);
        WraithPromotionService.clearPlayer(player);
        WraithTaskService.clearPlayer(player);
        SaboteurPlayerComponent.KEY.get(player).clear();
        wraith.clear();
        WraithRuntimeStateService.clear(player);
        if (wasActive) {
            WraithVoiceChannelService.restoreLivingChannel(player);
        }
    }

    static void clearRoundState(ServerWorld world) {
        WraithDeathService.clearAll();
        WraithPromotionService.clearAll();
        WraithTaskService.clearAll();
        for (ServerPlayerEntity player : world.getPlayers()) {
            clearPlayer(player);
        }
        WraithRoundQuotaService.clearRound(world);
    }

    public static void terminateAsSpectator(ServerPlayerEntity player) {
        clearPlayer(player);
        SparkTraitsWraithBridge.clearRuntimeTraits(player);
        PlayerMoodComponent.KEY.get(player).reset();
        player.changeGameMode(GameMode.SPECTATOR);
        WraithVoiceChannelService.moveToDeadChannel(player);
    }

    private static void onJoin(ServerPlayerEntity player) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!wraith.isActive()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        UUID uuid = player.getUuid();
        if (!WraithLifecycleRules.shouldResume(
                true,
                game.isRunning(),
                game.hasAnyRole(uuid),
                game.isPlayerDead(uuid)
        )) {
            clearPlayer(player);
            SparkTraitsWraithBridge.clearRuntimeTraits(player);
            return;
        }
        if (wraith.isRestricted() && !game.isRole(player, WraithRole.ROLE)) {
            WraithRoleTransitionService.transition(player, WraithRole.ROLE);
        }
        WraithRuntimeStateService.apply(player, wraith.isRestricted());
        WraithVoiceChannelService.restoreLivingChannel(player);
        WraithTaskService.resumePlayer(player);
        WraithPromotionService.resumePlayer(player);
    }
}
