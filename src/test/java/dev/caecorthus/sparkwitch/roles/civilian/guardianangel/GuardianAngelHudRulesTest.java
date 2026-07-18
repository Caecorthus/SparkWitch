package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardianAngelHudRulesTest {
    @Test
    void roundsCooldownUpToWholeSeconds() {
        assertEquals(1, GuardianAngelHudRules.cooldownSeconds(1));
        assertEquals(1, GuardianAngelHudRules.cooldownSeconds(20));
        assertEquals(2, GuardianAngelHudRules.cooldownSeconds(21));
    }

    @Test
    void selectsCooldownBeforeTargetPrompts() {
        assertEquals(GuardianAngelHudRules.State.COOLDOWN,
                GuardianAngelHudRules.state(20, false));
        assertEquals(GuardianAngelHudRules.State.AIM_AT_PLAYER,
                GuardianAngelHudRules.state(0, false));
        assertEquals(GuardianAngelHudRules.State.READY,
                GuardianAngelHudRules.state(0, true));
    }
}
