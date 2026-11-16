package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.net.UseHannaFloatC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Registers only Hanna-owned client presentation and sends her float request. Presentation: her own float line
 * ({@link HannaFloatHud}), from one confirmed-server-gated HUD callback; her mana is the shared mana row, which the
 * server turns on. The instinct veil ({@code hanna.HannaInstinctVetoMixin}) and the Presence Footsteps silence
 * ({@code hanna.HannaPresenceFootstepsVolumeMixin}) are client mixins that read the synced float of ANY player and need
 * no registration here. Server authority: the client never predicts the float; the key only sends the empty
 * {@code sparkwitch:use_hanna_float} request and the server validates role, round, cooldown, Fear and mana, then
 * resyncs {@code sparkwitch:hanna_player}. Never touches the {@code gui.sparkwitch.skills} panel.
 * 只注册汉娜自有的客户端展示，并发送她的浮游请求。展示：她自己的浮游行（{@link HannaFloatHud}），使用一个仅在已确认服务器上
 * 生效的 HUD 回调；她的魔力使用共享魔力行，由服务端开启。本能遮蔽（{@code hanna.HannaInstinctVetoMixin}）与 Presence
 * Footsteps 静音（{@code hanna.HannaPresenceFootstepsVolumeMixin}）是读取任意玩家同步浮游状态的客户端 mixin，无需在此注册。
 * 服务端权威：客户端从不预测浮游；按键只发送空的 {@code sparkwitch:use_hanna_float} 请求，职业、回合、冷却、恐惧与魔力全部
 * 由服务端校验，再重新同步 {@code sparkwitch:hanna_player}。从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class HannaClient {
    private static boolean initialized;

    private HannaClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                HannaFloatHud.render(context);
            }
        });
    }

    /**
     * Called by the shared ability-key dispatcher in {@code SparkWitchClient} when the local player's real role is
     * Hanna. Checks nothing locally (no cooldown, mana or liveness test) and skips the send only when the server has no
     * receiver for the request (fail closed).
     * 当本地玩家的真实职业为汉娜时，由 {@code SparkWitchClient} 的共享技能键分发调用。本地不做任何检查（不查冷却、魔力或
     * 存活），仅在服务端没有该请求的接收器时跳过发送（失败即关闭）。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client != null && client.player != null && ClientPlayNetworking.canSend(UseHannaFloatC2SPayload.ID)) {
            ClientPlayNetworking.send(new UseHannaFloatC2SPayload());
        }
    }
}
