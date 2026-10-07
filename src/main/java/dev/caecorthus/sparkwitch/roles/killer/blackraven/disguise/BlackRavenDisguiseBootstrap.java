package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapterBootstrap;

/**
 * Common-init entry of the disguise module, called once from BlackRavenFeatureService on both sides
 * so the adapter registry is also available to client shop and money reads.
 * 伪装模块的通用初始化入口，由 BlackRavenFeatureService 在双端各调用一次，使客户端商店与金钱读取也能使用适配器注册表。
 */
public final class BlackRavenDisguiseBootstrap {
    private static boolean registered;

    private BlackRavenDisguiseBootstrap() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackRavenDisguiseAdapterBootstrap.registerBatchOne();
        BlackRavenDisguiseService.register();
        BlackRavenDisguiseEconomy.register();
    }
}
