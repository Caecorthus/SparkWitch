package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Registers only Fiend-owned client presentation: the Fiend Moment countdown HUD (every player), the moment Fiend's
 * own Dash line and the dormant Fiend's own feign-death line (same bottom-right row; never both at once), all from
 * one confirmed-server-gated HUD callback. The two-way outline is the {@code fiend.WatheClientFiendHighlightMixin}
 * client mixin; the Dash key is dispatched by {@code SparkWitchClient} to {@link FiendDashClient}. Never touches the
 * {@code gui.sparkwitch.skills} panel.
 * 只注册魔人自有的客户端展示：魔人时刻倒计时 HUD（所有玩家）、时刻中魔人自己的疾驰行，以及休眠魔人自己的假死行
 * （同在右下角一行；魔人不会同时拥有两者），三者共用一个仅在已确认服务器上生效的 HUD 回调。双向描边由客户端 mixin
 * {@code fiend.WatheClientFiendHighlightMixin} 负责；疾驰按键由 {@code SparkWitchClient} 分发到 {@link FiendDashClient}。
 * 从不触及 {@code gui.sparkwitch.skills} 面板。
 */
public final class FiendClient {
    private static boolean initialized;

    private FiendClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                FiendMomentHud.render(context, tickCounter);
                FiendDashHud.render(context);
                FiendFeignHud.render(context);
            }
        });
    }
}
