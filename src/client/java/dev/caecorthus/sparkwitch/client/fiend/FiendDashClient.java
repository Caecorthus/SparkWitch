package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.net.UseFiendDashC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client side of Dash, the moment Fiend's ability-key skill. Server authority: the client only sends the empty
 * {@code sparkwitch:use_fiend_dash} request and never checks the Dash cooldown, the moment or liveness; the server
 * validates everything and resyncs the cooldown through {@code sparkwitch:fiend_moment}. Role-owned; never the
 * {@code gui.sparkwitch.skills} panel.
 * 疾驰（时刻中魔人的技能键技能）的客户端部分。服务端权威：客户端只发送空的 {@code sparkwitch:use_fiend_dash} 请求，
 * 从不检查疾驰冷却、时刻或存活状态；全部校验在服务端完成，冷却经由 {@code sparkwitch:fiend_moment} 重新同步。
 * 属于职业自有功能，从不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class FiendDashClient {
    private FiendDashClient() {
    }

    /**
     * Called by the shared ability-key dispatcher in {@code SparkWitchClient} when the local player is the moment
     * Fiend. Skips the send only when the server has no receiver for the request (fail closed).
     * 当本地玩家为时刻中的魔人时，由 {@code SparkWitchClient} 的共享技能键分发调用。仅在服务端没有该请求的接收器时
     * 跳过发送（失败即关闭）。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client != null && client.player != null && ClientPlayNetworking.canSend(UseFiendDashC2SPayload.ID)) {
            ClientPlayNetworking.send(new UseFiendDashC2SPayload());
        }
    }
}
