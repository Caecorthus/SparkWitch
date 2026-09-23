package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

/** Defines task income for active Wraith players with access to money. */
public final class WraithPromotionEconomyPolicy {
    public static final int BASE_TASK_REWARD = 50;

    private WraithPromotionEconomyPolicy() {
    }

    public static int balanceAfterPromotion(int currentBalance, boolean promotionSucceeded, boolean activeWraith) {
        return promotionSucceeded && activeWraith ? 0 : currentBalance;
    }

    public static int taskReward(boolean activeWraith, boolean canSeeMoney) {
        return activeWraith && canSeeMoney ? BASE_TASK_REWARD : 0;
    }
}
