package dev.caecorthus.sparkwitch.client.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurHudRulesTest {
    @Test
    void requiresConfirmedServerExactRoleAndPromotedWraith() {
        assertTrue(SaboteurHudRules.shouldRender(true, true, true));
        assertFalse(SaboteurHudRules.shouldRender(false, true, true));
        assertFalse(SaboteurHudRules.shouldRender(true, false, true));
        assertFalse(SaboteurHudRules.shouldRender(true, true, false));
    }

    @Test
    void roundsPositiveCooldownUpToWholeSeconds() {
        assertEquals(0, SaboteurHudRules.cooldownSeconds(-1));
        assertEquals(0, SaboteurHudRules.cooldownSeconds(0));
        assertEquals(1, SaboteurHudRules.cooldownSeconds(1));
        assertEquals(1, SaboteurHudRules.cooldownSeconds(20));
        assertEquals(2, SaboteurHudRules.cooldownSeconds(21));
        assertEquals(60, SaboteurHudRules.cooldownSeconds(1_200));
        assertEquals(120, SaboteurHudRules.cooldownSeconds(2_400));
    }
}
