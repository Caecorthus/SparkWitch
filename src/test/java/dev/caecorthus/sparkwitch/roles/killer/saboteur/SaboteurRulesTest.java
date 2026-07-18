package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurRulesTest {
    @Test
    void usesTheApprovedEconomyLightAndCooldownValues() {
        assertEquals(SaboteurRole.ID, SaboteurRules.ROLE_ID);
        assertEquals(50, SaboteurRules.TASK_REWARD);
        assertEquals(50, SaboteurRules.LOCKPICK_PRICE);
        assertEquals(20, SaboteurRules.LIGHT_RADIUS);
        assertEquals(400, SaboteurRules.LIGHT_DURATION_TICKS);
        assertEquals(1_200, SaboteurRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(2_400, SaboteurRules.COOLDOWN_TICKS);
    }

    @Test
    void activePromotionRequiresAllThreeIdentityFacts() {
        assertTrue(SaboteurRules.isActivePromotedSaboteur(true, true, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(false, true, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(true, false, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(true, true, false));
    }

    @Test
    void taskIncomeAndRecordedDeadShopAccessRequireActivePromotion() {
        assertTrue(SaboteurRules.shouldRewardTask(true));
        assertFalse(SaboteurRules.shouldRewardTask(false));
        assertTrue(SaboteurRules.canPassShopAliveGate(true, false));
        assertTrue(SaboteurRules.canPassShopAliveGate(false, true));
        assertFalse(SaboteurRules.canPassShopAliveGate(false, false));
    }
}
