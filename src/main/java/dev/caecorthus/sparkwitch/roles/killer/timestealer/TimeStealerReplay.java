package dev.caecorthus.sparkwitch.roles.killer.timestealer;

/**
 * Role-owned replay formatters for Clock thefts and stamp purchases (Wathe silently drops unformatted item uses).
 * 窃时者自有的回放格式化器：时钟窃取与邮票购买（Wathe 会静默丢弃未注册格式化器的物品使用记录）。
 */
public final class TimeStealerReplay {
    private TimeStealerReplay() {
    }

    public static void register() {
        // TODO(WP-03a): register item-use formatters for CLOCK_ID and STAMP_PURCHASE_RECORD_ID; the purchase
        // formatter reads TimeStealerRules.REPLAY_ENTRY_KEY (String entry id) and REPLAY_COST_KEY (int).
    }
}
