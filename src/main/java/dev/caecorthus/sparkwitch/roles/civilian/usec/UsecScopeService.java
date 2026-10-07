package dev.caecorthus.sparkwitch.roles.civilian.usec;

/**
 * Owned by WP4: the usec_scope receiver that writes UsecPlayerComponent.scoped. Called once from {@link UsecFeatureService#register()}.
 * 归 WP4 所有：写入 UsecPlayerComponent.scoped 的 usec_scope 接收器。由 {@link UsecFeatureService#register()} 调用一次。
 */
public final class UsecScopeService {
    private UsecScopeService() {
    }

    public static void register() {
    }
}
