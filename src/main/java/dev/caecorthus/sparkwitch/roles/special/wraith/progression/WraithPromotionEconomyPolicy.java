package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRules;
import net.minecraft.util.Identifier;

/** Defines post-promotion task income for active Wraith professions. */
public final class WraithPromotionEconomyPolicy {
    public static final int PROMOTED_TASK_REWARD = 50;

    private WraithPromotionEconomyPolicy() {
    }

    public static int balanceAfterPromotion(int currentBalance, boolean promotionSucceeded, boolean activeWraith) {
        return promotionSucceeded && activeWraith ? 0 : currentBalance;
    }

    public static int taskReward(boolean activeWraith, boolean promoted, Identifier roleId) {
        if (!activeWraith || !promoted) {
            return 0;
        }
        if (SparkWitchRoles.WIND_SPIRIT_ID.equals(roleId)) {
            // 风精灵是冤魂晋升身份，不在 NoellesRoles 原生任务金币白名单里；
            // 在冤魂晋升经济策略中单独补发，避免扩大到其他好人晋升职业。
            return WindSpiritRules.shouldRewardTask(true) ? PROMOTED_TASK_REWARD : 0;
        }
        return SparkWitchRoles.SABOTEUR_ID.equals(roleId)
                || SparkWitchRoles.CURSER_ID.equals(roleId)
                ? PROMOTED_TASK_REWARD
                : 0;
    }
}
