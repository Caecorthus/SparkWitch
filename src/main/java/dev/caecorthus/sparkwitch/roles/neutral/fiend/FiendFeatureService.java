package dev.caecorthus.sparkwitch.roles.neutral.fiend;

/**
 * Fiend registration hub: the single line in SparkWitchEvents. Each work package appends exactly one
 * {@code XxxService.register()} line here.
 * 魔人注册中心：SparkWitchEvents 中唯一的一行。每个工作包在此只追加一行 {@code XxxService.register()}。
 */
public final class FiendFeatureService {
    private static boolean registered;

    private FiendFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FiendReactionService.register();
        FiendMomentService.register();
    }
}
