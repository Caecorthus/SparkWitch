package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurPromotionRulesTest {
    @Test
    void activePromotedIdentityRequiresAllThreeLifecycleProperties() {
        assertTrue(SaboteurRules.isActivePromotedSaboteur(true, true, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(false, true, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(true, false, true));
        assertFalse(SaboteurRules.isActivePromotedSaboteur(true, true, false));
    }
}
