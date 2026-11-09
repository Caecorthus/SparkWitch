package dev.caecorthus.sparkwitch.roles.civilian.accountant;

/**
 * The Accountant's single server entry point, called once from {@code SparkWitchEvents}; idempotent.
 * 会计唯一的服务端入口，由 {@code SparkWitchEvents} 调用一次；可重复调用。
 */
public final class AccountantFeatureService {
    private static boolean registered;

    private AccountantFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AccountantEconomyService.register();
        AccountantShopService.register();
        AccountantPassbookService.register();
        AccountantReplay.register();
    }
}
