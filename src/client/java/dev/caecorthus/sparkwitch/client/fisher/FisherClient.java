package dev.caecorthus.sparkwitch.client.fisher;

/**
 * Angler client presentation: Glimmerfish outlines and HUD (held-item hiding and the Swordfish crosshair are mixins).
 * Role-owned presentation; never part of the witch skill inventory panel.
 * 钓鱼佬客户端表现：灵光鱼描边与 HUD（隐藏手持物与剑鱼准星由 mixin 实现）。属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class FisherClient {
    private static boolean registered;

    private FisherClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FisherGlimmerInstinctHooks.register();
        FisherGlimmerHud.register();
    }
}
