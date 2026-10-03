package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Session lifecycle edges (research 03 §4.3). Death and disconnect only clear the state and never touch the game mode
 * (Wathe already made the body a spectator, or kills it as ESCAPED next). A Last-Stand-intercepted death is
 * INTERCEPTED: SparkTraits now owns the body. Round edges ({@code ResetPlayer}, which Wathe fires after its own
 * ADVENTURE + spawn teleport, game start and finalize) clear silently with no cooldown. Everything else (role change,
 * gate loss, foreign moves, Depression, stay limit) is decided by the per-tick check. Server only.
 * 会话生命周期边界（调研 03 §4.3）。死亡与断线只清除状态，从不改动游戏模式（Wathe 已把本体设为旁观，或随后以 ESCAPED 处死）。
 * 被背水一战拦截的死亡记为 INTERCEPTED：本体此后归 SparkTraits 管理。对局边界（Wathe 在自身的冒险模式 + 出生点传送之后
 * 触发的 {@code ResetPlayer}、开局与结算）静默清理、不上冷却。其余情况（职业变化、门丢失、外力移动、抑郁、停留上限）
 * 都由逐刻检查判定。仅服务端。
 */
final class RiftSessionLifecycle {
    private static boolean registered;

    private RiftSessionLifecycle() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim != null && RiftSessionService.isInside(victim)) {
                RiftSessionService.forceExit(victim, WitchFactorTraitsBridge.isDeathIntercepted(victim)
                        ? RiftExitReason.INTERCEPTED : RiftExitReason.DIED);
            }
        });

        ResetPlayer.EVENT.register(RiftSessionLifecycle::clearSilently);

        // A stale session or cooldown from an aborted round never enters the new one. / 中止回合残留的状态不会进入新回合。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearAll(serverWorld);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearAll(serverWorld);
            }
        });

        // Only the session ends here; Wathe's deferred ESCAPED kill then drops the body at the gate.
        // 这里只结束会话；随后 Wathe 延迟执行的 ESCAPED 击杀会把尸体留在门处。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                RiftSessionService.forceExit(handler.player, RiftExitReason.DISCONNECTED);
            }
        });
    }

    /** ROUND_END: drops the session and the cooldown, never the mode or position. / 清除会话与冷却，不动模式与位置。 */
    static void clearSilently(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (!RiftSessionService.forceExit(player, RiftExitReason.ROUND_END)) {
            RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
            if (session != null) {
                session.clear();
            }
        }
    }

    private static void clearAll(ServerWorld world) {
        for (ServerPlayerEntity player : List.copyOf(world.getServer().getPlayerManager().getPlayerList())) {
            clearSilently(player);
        }
    }
}
