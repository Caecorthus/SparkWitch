package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.roles.civilian.athlete.net.UseAthleteDashC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client side of the Athlete's ability-key Dash. Server authority: the client only sends the empty
 * {@code sparkwitch:use_athlete_dash} request and never checks the cooldown, the ground path or liveness; the server
 * validates everything and resyncs the cooldown through the owner-only {@code sparkwitch:athlete} component.
 * Role-owned; never the {@code gui.sparkwitch.skills} panel.
 * 运动员技能键冲刺的客户端部分。服务端权威：客户端只发送空的 {@code sparkwitch:use_athlete_dash} 请求，从不检查冷却、
 * 地面路径或存活状态；全部校验在服务端完成，冷却经由仅本人同步的 {@code sparkwitch:athlete} 组件重新同步。
 * 属于职业自有功能，从不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class AthleteDashClient {
    private AthleteDashClient() {
    }

    /**
     * Called by the shared ability-key dispatcher in {@code SparkWitchClient} when the local player's real role is the
     * Athlete. Skips the send only when the server has no receiver for the request (fail closed).
     * 当本地玩家的真实职业为运动员时，由 {@code SparkWitchClient} 的共享技能键分发调用。仅在服务端没有该请求的接收器时
     * 跳过发送（失败即关闭）。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client != null && client.player != null && ClientPlayNetworking.canSend(UseAthleteDashC2SPayload.ID)) {
            ClientPlayNetworking.send(new UseAthleteDashC2SPayload());
        }
    }
}
