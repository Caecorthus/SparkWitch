package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.minecraft.util.Identifier;

/** Defines post-promotion task income for active Wraith professions. */
public final class WraithPromotionEconomyPolicy {
    public static final int NON_GOOD_TASK_REWARD = 50;

    private WraithPromotionEconomyPolicy() {
    }

    public static int balanceAfterPromotion(int currentBalance, boolean promotionSucceeded, boolean activeWraith) {
        return promotionSucceeded && activeWraith ? 0 : currentBalance;
    }

    public static int taskReward(boolean activeWraith, boolean promoted, Identifier roleId) {
        if (!activeWraith || !promoted) {
            return 0;
        }
        return SparkWitchRoles.SABOTEUR_ID.equals(roleId)
                || SparkWitchRoles.CURSER_ID.equals(roleId)
                ? NON_GOOD_TASK_REWARD
                : 0;
    }
}
