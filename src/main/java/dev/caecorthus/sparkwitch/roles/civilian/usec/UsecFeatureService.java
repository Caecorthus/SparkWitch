package dev.caecorthus.sparkwitch.roles.civilian.usec;

/**
 * USEC registration hub: the single line in SparkWitchEvents (appended last). Each work package owns its own
 * {@code register()}; this fixed order is part of the contract because it is the listener order within each event.
 * USEC 注册中心：SparkWitchEvents 中唯一的一行（追加在末尾）。各工作包拥有自己的 {@code register()}；
 * 这一固定顺序属于契约，因为它决定了各事件内的监听器顺序。
 */
public final class UsecFeatureService {
    private static boolean registered;

    private UsecFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UsecLifecycleService.register();
        UsecEconomyService.register();
        UsecShopService.register();
        UsecRifleFireService.register();
        UsecAttachmentService.register();
        UsecScopeService.register();
    }
}
