package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionCenter;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHitService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowNetworking;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Event wiring for Raven Form: payloads, the two-task unlock, the body's collision exemption, the perception centre
 * override, chunk streaming and every lifecycle end (role change, death, reset, disconnect, round end). Runs from the
 * common initializer, so the payload types exist on both sides; only the server acts.
 * 化鸦的事件接线：数据包、两个任务解锁、本体碰撞豁免、感知中心覆盖、区块流式加载，以及所有生命周期结束（换职业、死亡、重置、
 * 断线、回合结束）。在通用初始化器中运行，因此数据包类型在两端都存在；只有服务端执行。
 */
public final class BlackRavenCrowFeatureService {
    private static boolean registered;

    private BlackRavenCrowFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackRavenCrowNetworking.register();
        BlackRavenCrowStreaming.register();
        BlackRavenCrowSkillService.registerReplayFormatter();
        // Weapon hits on the crow (crow-hits). / 武器命中乌鸦（crow-hits）。
        BlackRavenCrowHitService.register();
        // The left-behind body neither pushes nor is pushed, for everyone: the predicate reads the active bit, which
        // every client receives, so each client's own collision prediction agrees with the server.
        // 被留下的本体对所有人都不推挤也不被推挤：谓词读取所有客户端都会收到的在场标记，因此各客户端的本地碰撞预测与服务端一致。
        SparkFactionApi.registerEntityCollisionExemption(BlackRavenCrowForm::isHiddenBody);
        // Perception measures from the crow during a session (plan §3.5). / 会话期间感知从乌鸦位置测量（计划 §3.5）。
        BlackRavenPerceptionCenter.setOverride(BlackRavenCrowSessions::perceptionCentre);
        TaskComplete.EVENT.register((player, taskType) -> BlackRavenCrowSkillService.onTaskComplete(player));
        // Any new role starts over (a re-dealt Black Raven too). / 任何新职业都从头开始（重新发到黑羽鸦也一样）。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BlackRavenCrowSessions.endAndClear(serverPlayer, BlackRavenCrowEndReason.ROLE_LOST);
            }
        });
        // Any death ends the session; a SparkTraits-intercepted death (the player stays in play) keeps the unlock.
        // 任何死亡都会结束会话；被 SparkTraits 拦截的死亡（玩家仍留在对局中）保留解锁进度。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                BlackRavenCrowSessions.end(victim, BlackRavenCrowEndReason.DIED);
            } else {
                BlackRavenCrowSessions.endAndClear(victim, BlackRavenCrowEndReason.DIED);
            }
        });
        ResetPlayer.EVENT.register(player ->
                BlackRavenCrowSessions.endAndClear(player, BlackRavenCrowEndReason.ROUND_END));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BlackRavenCrowSessions.end(handler.player, BlackRavenCrowEndReason.DISCONNECTED));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlackRavenCrowSessions.endAndClear(player, BlackRavenCrowEndReason.ROUND_END);
            }
            BlackRavenCrowSessions.discardAll(serverWorld);
        });
    }
}
