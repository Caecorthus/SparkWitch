package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.roles.civilian.planner.net.UsePlannerRecommendC2SPayload;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client side of the Master Planner's recommendation on the shared ability key. It registers no key binding and no
 * HUD callback: {@code SparkWitchClient}'s ability-key dispatch calls {@link #onAbilityKey} for the raw planner role on
 * a confirmed SparkWitch server, and her two corner rows are the {@code RoleHudDispatcher.renderPlanner} layer
 * ({@link PlannerHud}). Server authority: the press sends {@code sparkwitch:use_planner_recommend} with the UUID of the
 * player under the crosshair ({@link PlannerTargeting}), or her own; it never checks the cooldown, coins or the
 * target's tasks, so every refusal comes from the server. Role-owned; never the {@code gui.sparkwitch.skills} panel.
 * 规划大师共享技能键推荐的客户端部分。不注册按键、也不注册 HUD 回调：已确认的 SparkWitch 服务器上，{@code SparkWitchClient}
 * 的技能键分发对真实职业为规划大师的玩家调用 {@link #onAbilityKey}，她的两行角落读数是 {@code RoleHudDispatcher.renderPlanner}
 * 层（{@link PlannerHud}）。服务端权威：按下时发送 {@code sparkwitch:use_planner_recommend}，携带准星下玩家的 UUID
 * （{@link PlannerTargeting}），没有目标时为她自己的 UUID；从不检查冷却、金币或目标的任务，因此所有拒绝都来自服务端。
 * 属于职业自有功能，从不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class PlannerClient {
    private PlannerClient() {
    }

    /**
     * Called by the shared ability-key dispatcher when the local player's real role is the planner. Sends nothing for
     * a dead, non-participant or spectating player (there is no one to aim for), or when the server has no receiver for
     * the request (fail closed).
     * 当本地玩家的真实职业为规划大师时，由共享技能键分发调用。死亡、未参与或旁观的玩家不发送（无从瞄准），服务端没有该请求的
     * 接收器时也不发送（失败即关闭）。
     */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || player.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !ClientPlayNetworking.canSend(UsePlannerRecommendC2SPayload.ID)) {
            return;
        }
        PlayerEntity aimed = PlannerTargeting.aimedPlayer(player);
        ClientPlayNetworking.send(new UsePlannerRecommendC2SPayload(
                PlannerTargeting.payloadTarget(player.getUuid(), aimed == null ? null : aimed.getUuid())));
    }
}
