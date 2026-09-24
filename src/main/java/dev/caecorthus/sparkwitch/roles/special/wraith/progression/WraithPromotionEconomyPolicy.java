package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRules;

/** Defines task income for active Wraith players with access to money. */
public final class WraithPromotionEconomyPolicy {
    public static final int BASE_TASK_REWARD = 50;

    private WraithPromotionEconomyPolicy() {
    }

    public static int balanceAfterPromotion(int currentBalance, boolean promotionSucceeded, boolean activeWraith) {
        return promotionSucceeded && activeWraith ? 0 : currentBalance;
    }

    public static int taskReward(boolean activeWraith, boolean canSeeMoney, boolean promotedWindSpirit) {
        if (!activeWraith) {
            return 0;
        }
        // Wind Spirit is a Wraith promotion outside NoellesRoles' native task-coin whitelist,
        // so it is paid here even when money visibility does not grant it.
        // 风精灵是冤魂晋升身份，不在 NoellesRoles 原生任务金币白名单里，
        // 即使金钱可见性未放行也在此单独补发。
        return canSeeMoney || WindSpiritRules.shouldRewardTask(promotedWindSpirit) ? BASE_TASK_REWARD : 0;
    }
}
