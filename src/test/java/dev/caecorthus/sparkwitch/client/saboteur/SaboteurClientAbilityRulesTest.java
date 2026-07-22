package dev.caecorthus.sparkwitch.client.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurClientAbilityRulesTest {
    @Test
    void sendsOnlyForTheConfirmedPromotedSaboteurChannel() {
        assertTrue(SaboteurClientAbilityRules.shouldSend(true, true, true, true));
        assertFalse(SaboteurClientAbilityRules.shouldSend(false, true, true, true));
        assertFalse(SaboteurClientAbilityRules.shouldSend(true, false, true, true));
        assertFalse(SaboteurClientAbilityRules.shouldSend(true, true, false, true));
        assertFalse(SaboteurClientAbilityRules.shouldSend(true, true, true, false));
    }
}
