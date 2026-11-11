package dev.caecorthus.sparkwitch.roles.civilian.medium;

/**
 * Medium server registration hub: the single line in {@code SparkWitchEvents}, placed after the BEAR hub and before
 * the USEC hub (USEC stays last). Each work package owns its own {@code register()} and appends it here; this fixed
 * order is part of the contract because it is the listener order within each event.
 * 通灵师服务端注册中心：{@code SparkWitchEvents} 中唯一的一行，紧挨 BEAR 注册中心之后、USEC 注册中心之前（USEC 保持
 * 最后）。各工作包拥有自己的 {@code register()} 并追加在此；这一固定顺序属于契约，因为它决定了各事件内的监听器顺序。
 */
public final class MediumFeatureService {
    private static boolean registered;

    private MediumFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MediumEconomyService.register();
        MediumSkillService.register();
        MediumSecondSkillService.register();
    }
}
