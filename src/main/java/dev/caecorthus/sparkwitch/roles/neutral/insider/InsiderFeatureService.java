package dev.caecorthus.sparkwitch.roles.neutral.insider;

/**
 * Insider registration hub: the single line in SparkWitchEvents. Each work package appends exactly one
 * {@code XxxService.register()} line here.
 * 内应注册中心：SparkWitchEvents 中唯一的一行。每个工作包在此只追加一行 {@code XxxService.register()}。
 */
public final class InsiderFeatureService {
    private static boolean registered;

    private InsiderFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        InsiderEquipmentService.register();
    }
}
