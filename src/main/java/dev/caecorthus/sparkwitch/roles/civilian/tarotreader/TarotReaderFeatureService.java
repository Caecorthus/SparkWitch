package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Owns Tarot Reader event wiring while SparkWitchEvents stays an aggregator. */
public final class TarotReaderFeatureService {
    private static boolean registered;

    private TarotReaderFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        TarotReaderShopService.register();
        TarotReaderEconomyService.register();
        GameEvents.ON_GAME_START.register(gameMode -> {
            TarotReaderSelectionSessionService.clearAll();
            TarotReaderRoundRoleHistory.clear();
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                TarotReaderRoundRoleHistory.record(role);
                TarotReaderEconomyService.assignForRole(serverPlayer, role);
                if (!TarotReaderRules.isTarotReader(role)) {
                    TarotReaderSelectionSessionService.clear(serverPlayer.getUuid());
                }
            }
        });
        // Rebuild round-start history from final roles: SparkTraits Conscience compensation
        // (TraitAssignmentService.addExtraKillersForConscience) overwrites a civilian's role after Wathe's
        // RoleAssigned loop already reported it. This snapshot also keeps original roles later overwritten by
        // Wraith conversion, Grand Witch recruitment or Loose End, so keep it even if record() is ever status-gated.
        // 开局历史按最终身份重建：SparkTraits 良心补偿会在 Wathe 的 RoleAssigned 循环上报之后改写某位好人的身份；
        // 此快照同时保留之后被冤魂转化、招募或亡命徒改写的原身份，即使 record() 日后按对局状态过滤也必须保留。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld) {
                TarotReaderRoundRoleHistory.resetTo(game.getRoles().values());
            }
        });
        TaskComplete.EVENT.register((player, taskType) -> TarotReaderEconomyService.onTaskComplete(player));
        KillPlayer.AFTER.register((victim, killer, deathReason) ->
                TarotReaderSelectionSessionService.clear(victim.getUuid()));
        ResetPlayer.EVENT.register(player -> TarotReaderSelectionSessionService.clear(player.getUuid()));
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld) {
                TarotReaderSelectionSessionService.clearAll();
                TarotReaderRoundRoleHistory.clear();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TarotReaderSelectionSessionService.clear(handler.getPlayer().getUuid()));
    }
}
