package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianAngelRulesTest {
    @Test
    void exposesApprovedIdsColorCooldownRangeAndDuration() {
        assertEquals(Identifier.of("sparkwitch", "guardian_angel"), GuardianAngelRules.ROLE_ID);
        assertEquals(Identifier.of("sparkwitch", "guardian"), GuardianAngelRules.SKILL_ID);
        assertEquals(0x36E51B, GuardianAngelRules.COLOR);
        assertEquals(1_200, GuardianAngelRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(1_800, GuardianAngelRules.POST_USE_COOLDOWN_TICKS);
        assertEquals(200, GuardianAngelRules.SHIELD_DURATION_TICKS);
        assertEquals(3.0D, GuardianAngelRules.TARGET_RANGE);
        assertEquals(9.0D, GuardianAngelRules.TARGET_RANGE_SQUARED);
    }

    @Test
    void targetingRejectsSelfDeadShieldedHiddenAndOutOfRangePlayers() {
        assertTrue(GuardianAngelRules.canTarget(false, true, false, true, 9.0D));
        assertFalse(GuardianAngelRules.canTarget(true, true, false, true, 1.0D));
        assertFalse(GuardianAngelRules.canTarget(false, false, false, true, 1.0D));
        assertFalse(GuardianAngelRules.canTarget(false, true, true, true, 1.0D));
        assertFalse(GuardianAngelRules.canTarget(false, true, false, false, 1.0D));
        assertFalse(GuardianAngelRules.canTarget(false, true, false, true, Math.nextUp(9.0D)));
    }

    @Test
    void deathProtectionMatchesIronManAndExplicitFallExclusions() {
        assertFalse(GuardianAngelRules.shouldBlockDeath(GameConstants.DeathReasons.SHOT_INNOCENT));
        assertFalse(GuardianAngelRules.shouldBlockDeath(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN));
        assertFalse(GuardianAngelRules.shouldBlockDeath(Identifier.of("noellesroles", "assassinated")));
        assertFalse(GuardianAngelRules.shouldBlockDeath(Identifier.of("noellesroles", "voodoo")));
        assertTrue(GuardianAngelRules.shouldBlockDeath(GameConstants.DeathReasons.KNIFE));
    }
}
