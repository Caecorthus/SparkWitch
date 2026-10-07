package dev.caecorthus.sparkwitch.client.scope;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Client only. Owned by WP4: the reusable scope renderer (zoom, blur, lens, reticle); USEC and later the Potion Gunner launcher pass their own parameters. Called once from {@code UsecClientModule.register()}.
 * 仅客户端。归 WP4 所有：可复用的开镜渲染（放大、模糊、镜片、分划）；USEC 与之后的药炮手炮筒各自传入参数。由 {@code UsecClientModule.register()} 调用一次。
 * <p>WP4-usec compile stub of the frozen WP4-scope API (declarations only); the coordinator keeps WP4-scope's real
 * file at merge. / WP4-usec 针对冻结 API 的编译桩（仅声明）；合并时由协调者保留 WP4-scope 的真实文件。
 */
public final class ScopeClient {
    private ScopeClient() {
    }

    public static void register() {
    }

    public static void registerProvider(Supplier<@Nullable ScopeProfile> provider) {
    }

    public static @Nullable ScopeProfile activeProfile() {
        return null;
    }

    public static boolean isScoped() {
        return false;
    }

    public static ScopeMode effectiveMode() {
        return ScopeMode.ZOOM_BLUR;
    }
}
