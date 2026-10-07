package dev.caecorthus.sparkwitch.client.scope;

/**
 * Client only. Owned by WP4: the reusable scope renderer (zoom, blur, lens, reticle); USEC and later the Potion Gunner launcher pass their own parameters. Called once from {@code UsecClientModule.register()}.
 * 仅客户端。归 WP4 所有：可复用的开镜渲染（放大、模糊、镜片、分划）；USEC 与之后的药炮手炮筒各自传入参数。由 {@code UsecClientModule.register()} 调用一次。
 */
public final class ScopeClient {
    private ScopeClient() {
    }

    public static void register() {
    }
}
