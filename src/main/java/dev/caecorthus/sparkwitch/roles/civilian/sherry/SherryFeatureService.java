package dev.caecorthus.sparkwitch.roles.civilian.sherry;

/**
 * 橘雪莉 server registration hub: the single line in {@code SparkWitchEvents}, placed after the Medium hub and before
 * the USEC hub (USEC stays last). The fixed order below is the listener order within each event.
 * 橘雪莉服务端注册中心：{@code SparkWitchEvents} 中唯一的一行，位于通灵师注册中心之后、USEC 注册中心之前（USEC 保持最后）。
 * 下方的固定顺序即各事件内的监听器顺序。
 */
public final class SherryFeatureService {
    private static boolean registered;

    private SherryFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SherryEconomyService.register();
        SherryDeductionService.register();
        SherryMightyForceService.register();
        SherryBadgeService.register();
    }
}
