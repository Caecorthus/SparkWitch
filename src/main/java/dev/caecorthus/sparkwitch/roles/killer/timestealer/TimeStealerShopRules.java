package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import org.jetbrains.annotations.Nullable;

/**
 * Pure Time Stealer shop plan: stamp prices per entry id and the native-list rewrite. Entry ids are frozen; the psycho
 * entry keeps Wathe's {@code psycho_mode} id because SparkStrength, SparkTraits and Wathe cooldowns key on it.
 * 纯窃时者商店规划：各商品 id 的邮票价格与原生列表改写。商品 id 已冻结；疯魔商品保留 Wathe 的 {@code psycho_mode} id，
 * 因为 SparkStrength、SparkTraits 与 Wathe 冷却都依赖它。
 */
public final class TimeStealerShopRules {
    public static final String GRENADE_ENTRY_ID = "sparkwitch_time_stealer_grenade";
    public static final String PSYCHO_ENTRY_ID = "psycho_mode";
    public static final String ADD_TIME_ENTRY_ID = "sparkwitch_time_stealer_add_time";

    private TimeStealerShopRules() {
    }

    /** Stamp price of a Time Stealer entry, or 0 for coin-priced and foreign entries. / 窃时者商品的邮票价格；金币商品与其他商品为 0。 */
    public static int stampCost(@Nullable String entryId) {
        // TODO(WP-05): GRENADE 3, PSYCHO 3, ADD_TIME 1 from TimeStealerRules.
        return 0;
    }
}
