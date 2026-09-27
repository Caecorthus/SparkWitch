package dev.caecorthus.sparkwitch.roles.killer.timestealer;

/**
 * Restricted Time Stealer shop: a role-gated {@code BuildShopEntries} rewrite and a deny-only, role-gated
 * {@code ShopPurchase.BEFORE}. Other killers' shops are untouched.
 * 受限的窃时者商店：按职业门控的 {@code BuildShopEntries} 改写，以及按职业门控、只拒绝的 {@code ShopPurchase.BEFORE}。
 * 其他杀手的商店不受影响。
 */
public final class TimeStealerShopService {
    private TimeStealerShopService() {
    }

    public static void register() {
        // TODO(WP-05): BuildShopEntries listener + deny-only BEFORE (never allow).
    }
}
