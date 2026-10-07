package dev.caecorthus.sparkwitch.client.usec;

/**
 * Client only. Owned by WP7: the usec_bullet_impacts receiver, block cracks and hit particles. Called once from {@code UsecClientModule.register()}.
 * 仅客户端。归 WP7 所有：usec_bullet_impacts 接收器、方块裂痕与命中粒子。由 {@code UsecClientModule.register()} 调用一次。
 */
public final class UsecImpactClient {
    private UsecImpactClient() {
    }

    public static void register() {
    }
}
