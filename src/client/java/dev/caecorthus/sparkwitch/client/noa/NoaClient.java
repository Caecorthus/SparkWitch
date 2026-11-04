package dev.caecorthus.sparkwitch.client.noa;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Registers only Noa-owned client presentation: Noa's own painting line, from one confirmed-server-gated HUD callback.
 * The painting key is dispatched by {@code SparkWitchClient} to {@link NoaPaintClient}; her cyan mood icon is the
 * {@code noa.NoaMoodRendererMixin} client mixin. Never touches the {@code gui.sparkwitch.skills} panel and never shows
 * her sanity value.
 * 只注册诺亚自有的客户端展示：诺亚自己的作画行，使用一个仅在已确认服务器上生效的 HUD 回调。作画按键由
 * {@code SparkWitchClient} 分发到 {@link NoaPaintClient}；她的青色心情图标由客户端 mixin
 * {@code noa.NoaMoodRendererMixin} 负责。从不触及 {@code gui.sparkwitch.skills} 面板，也从不显示她的理智值。
 */
public final class NoaClient {
    private static boolean initialized;

    private NoaClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                NoaPaintHud.render(context);
            }
        });
    }
}
