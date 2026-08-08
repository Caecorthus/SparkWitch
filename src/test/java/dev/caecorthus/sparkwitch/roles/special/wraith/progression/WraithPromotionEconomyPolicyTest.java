package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.Test;

class WraithPromotionEconomyPolicyTest {
    @Test
    void onlySuccessfulActivePromotionResetsExistingBalance() {
        assertEquals(0, WraithPromotionEconomyPolicy.balanceAfterPromotion(275, true, true));
        assertEquals(275, WraithPromotionEconomyPolicy.balanceAfterPromotion(275, false, true));
        assertEquals(275, WraithPromotionEconomyPolicy.balanceAfterPromotion(275, true, false));
    }

    @Test
    void onlyActiveApprovedPromotionsEarnPostPromotionTaskIncome() {
        assertEquals(50, WraithPromotionEconomyPolicy.taskReward(true, true, SparkWitchRoles.WIND_SPIRIT_ID));
        assertEquals(0, WraithPromotionEconomyPolicy.taskReward(true, true, SparkWitchRoles.GUARDIAN_ANGEL_ID));
        assertEquals(0, WraithPromotionEconomyPolicy.taskReward(true, true, SparkWitchRoles.VENDETTA_ID));
        assertEquals(50, WraithPromotionEconomyPolicy.taskReward(true, true, SparkWitchRoles.SABOTEUR_ID));
        assertEquals(50, WraithPromotionEconomyPolicy.taskReward(true, true, SparkWitchRoles.CURSER_ID));
        assertEquals(0, WraithPromotionEconomyPolicy.taskReward(false, true, SparkWitchRoles.SABOTEUR_ID));
        assertEquals(0, WraithPromotionEconomyPolicy.taskReward(true, false, SparkWitchRoles.CURSER_ID));
        assertEquals(0, WraithPromotionEconomyPolicy.taskReward(true, true, null));
    }
}
