package dev.caecorthus.sparkwitch.client.potiongunner;

/**
 * Potion Gunner client entry point: scope zoom and overlay, fire input, loaded-shell HUD, and the shell renderer.
 * Presentation only; the server decides every shot and blast.
 * 药炮手客户端入口：瞄准镜缩放与遮罩、开火输入、装填 HUD 与炮弹渲染。仅负责展示；每次发射与爆炸都由服务端决定。
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
        // WP4 implements. / 由 WP4 实现。
    }
}
