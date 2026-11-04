package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Registers only Athlete-owned client presentation: the Athlete's own Dash line, from one confirmed-server-gated HUD
 * callback. The Dash key is dispatched by {@code SparkWitchClient} to {@link AthleteDashClient}. Never touches the
 * {@code gui.sparkwitch.skills} panel.
 * 只注册运动员自有的客户端展示：运动员自己的冲刺行，使用一个仅在已确认服务器上生效的 HUD 回调。冲刺按键由
 * {@code SparkWitchClient} 分发到 {@link AthleteDashClient}。从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class AthleteClient {
    private static boolean initialized;

    private AthleteClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                AthleteDashHud.render(context);
            }
        });
    }
}
