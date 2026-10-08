package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;

/**
 * Client only. USEC client registration hub: the single line in SparkWitchClient (appended last). Each work package
 * owns its own {@code register()}; this fixed order is part of the contract.
 * 仅客户端。USEC 客户端注册中心：SparkWitchClient 中唯一的一行（追加在末尾）。各工作包拥有自己的 {@code register()}；
 * 这一固定顺序属于契约。
 */
public final class UsecClientModule {
    private static boolean registered;

    private UsecClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ScopeClient.register();
        UsecHudClient.register();
        UsecAttachmentClient.register();
        UsecImpactClient.register();
        UsecRifleModels.register();
        UsecBoltSwayClient.register();
    }
}
