package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Potion Gunner client entry point: the launcher scope, fire input, loaded-shell HUD, and the shell renderer.
 * Presentation only; the server decides every shot and blast. The scope runs on the shared {@code client/scope}
 * module: this method makes sure the module is registered ({@code ScopeClient.register()} is idempotent, so it does
 * not matter whether this or {@code UsecClientModule} initialises first) and adds {@link PotionScopeProfile}'s
 * provider once. It also registers the HUD line and the fire latch's tick and disconnect resets; the mixins in
 * {@code client/mixin/potiongunner/} read the state kept here. It never touches the witch skill inventory panel
 * (AGENTS.md).
 * 药炮手客户端入口：炮筒瞄准镜、开火输入、装填 HUD 与炮弹渲染。仅负责展示；每次发射与爆炸都由服务端决定。瞄准镜运行在
 * 共享的 {@code client/scope} 模块上：本方法确保模块已注册（{@code ScopeClient.register()} 是幂等的，因此本方法与
 * {@code UsecClientModule} 谁先初始化都无妨），并添加一次 {@link PotionScopeProfile} 的提供者。它还注册 HUD 行，以及开火
 * 闩锁的逐刻与断线重置；{@code client/mixin/potiongunner/} 中的 mixin 读取这里维护的状态。从不触及魔女技能背包面板
 * （AGENTS.md）。
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
        ScopeClient.register();
        ScopeClient.registerProvider(PotionScopeProfile::provide);
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
