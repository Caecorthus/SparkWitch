package dev.caecorthus.sparkwitch.roles.civilian.bear;

/**
 * BEAR server registration hub: the single line in {@code SparkWitchEvents}, placed right before the USEC hub (USEC
 * stays last). Each work package owns its own {@code register()} and appends it here; this fixed order is part of the
 * contract because it is the listener order within each event.
 * BEAR 服务端注册中心：{@code SparkWitchEvents} 中唯一的一行，紧挨 USEC 注册中心之前（USEC 保持最后）。各工作包拥有自己的
 * {@code register()} 并追加在此；这一固定顺序属于契约，因为它决定了各事件内的监听器顺序。
 */
public final class BearFeatureService {
    private static boolean registered;

    private BearFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BearLifecycleService.register();
        BearEconomyService.register();
        BearShopService.register();
    }
}
