package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Registers only Fiend-owned client presentation: the Fiend Moment countdown HUD. The two-way outline is the
 * {@code fiend.WatheClientFiendHighlightMixin} client mixin. Never touches the {@code gui.sparkwitch.skills} panel.
 * 只注册魔人自有的客户端展示：魔人时刻倒计时 HUD。双向描边由客户端 mixin {@code fiend.WatheClientFiendHighlightMixin}
 * 负责。从不触及 {@code gui.sparkwitch.skills} 面板。
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
            }
        });
    }
}
