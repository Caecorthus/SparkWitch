package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.koko.net.UseKokoStreamC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Registers only Koko-owned client presentation and sends her go-live request. Presentation: her traffic and stream
 * rows ({@link KokoStreamHud}), from one confirmed-server-gated HUD callback, and her instinct outline of the other
 * players while she is live ({@link KokoInstinctHooks}); her mana is the shared mana row, which the server turns on.
 * Server authority: the client never predicts a stream; the key only sends the empty
 * {@code sparkwitch:use_koko_stream} request and the server validates role, round, control states, cooldown, Fear and
 * mana, then resyncs {@code sparkwitch:koko_player}. The corpse stream needs no client code: it is the vanilla
 * right-click on a body, answered in the common {@code KokoStreamService}. Never touches the
 * {@code gui.sparkwitch.skills} panel.
 * 只注册可可自有的客户端展示，并发送她的开播请求。展示：流量行与直播行（{@link KokoStreamHud}），使用一个仅在已确认服务器上
 * 生效的 HUD 回调；以及直播期间对其他玩家的本能描边（{@link KokoInstinctHooks}）；她的魔力使用共享魔力行，由服务端开启。
 * 服务端权威：客户端从不预测直播；按键只发送空的 {@code sparkwitch:use_koko_stream} 请求，职业、回合、受控状态、冷却、恐惧与
 * 魔力全部由服务端校验，再重新同步 {@code sparkwitch:koko_player}。尸体直播不需要客户端代码：它就是对尸体的原版右键，由通用
 * 端的 {@code KokoStreamService} 作答。从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class KokoClient {
    private static boolean initialized;

    private KokoClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        KokoInstinctHooks.register();
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                KokoStreamHud.render(context);
            }
        });
    }

    /**
     * Called by the shared ability-key dispatcher in {@code SparkWitchClient} when the local player's real role is
     * Koko. Checks nothing locally (no cooldown, mana or liveness test) and skips the send only when the server has no
     * receiver for the request (fail closed).
     * 当本地玩家的真实职业为可可时，由 {@code SparkWitchClient} 的共享技能键分发调用。本地不做任何检查（不查冷却、魔力或
     * 存活），仅在服务端没有该请求的接收器时跳过发送（失败即关闭）。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client != null && client.player != null && ClientPlayNetworking.canSend(UseKokoStreamC2SPayload.ID)) {
            ClientPlayNetworking.send(new UseKokoStreamC2SPayload());
        }
    }
}
