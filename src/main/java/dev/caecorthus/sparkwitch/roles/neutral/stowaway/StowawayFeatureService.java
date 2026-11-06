package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

/**
 * Stowaway registration hub: the single line in SparkWitchEvents.
 * 偷渡客注册中心：SparkWitchEvents 中的唯一一行。
 */
public final class StowawayFeatureService {
    private static boolean registered;

    private StowawayFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        StowawayLoadoutService.register();
        StowawayKillRewardService.register();
    }
}
