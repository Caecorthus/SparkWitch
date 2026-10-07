package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Potion Gunner client entry point: scope zoom and overlay, fire input, loaded-shell HUD, and the shell renderer.
 * Presentation only; the server decides every shot and blast. The mixins in {@code client/mixin/potiongunner/} read
 * the state kept here; this method registers only the HUD line and the fire latch's tick and disconnect resets. It
 * never touches the witch skill inventory panel (AGENTS.md).
 * 药炮手客户端入口：瞄准镜缩放与遮罩、开火输入、装填 HUD 与炮弹渲染。仅负责展示；每次发射与爆炸都由服务端决定。
 * {@code client/mixin/potiongunner/} 中的 mixin 读取这里维护的状态；本方法只注册 HUD 行，以及开火闩锁的逐刻与断线重置。
 * 从不触及魔女技能背包面板（AGENTS.md）。
 */
public final class PotionGunnerClient {
    private static boolean initialized;

    private PotionGunnerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register(PotionGunnerHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                PotionFireInput.reset();
                return;
            }
            PotionFireInput.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PotionFireInput.reset());
    }
}
