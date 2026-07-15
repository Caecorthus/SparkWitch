package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrthopedistRulesTest {
    @Test
    void exposesApprovedRoleCooldownRangeAndBuffTuning() {
        assertEquals(Identifier.of("sparkwitch", "orthopedist"), OrthopedistRules.ROLE_ID);
        assertEquals(0x90B358, OrthopedistRules.COLOR);
        assertEquals(600, OrthopedistRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(1_200, OrthopedistRules.POST_USE_COOLDOWN_TICKS);
        assertEquals(3.0D, OrthopedistRules.TARGET_RANGE);
        assertEquals(400, OrthopedistRules.BONE_SETTING_TICKS);
        assertEquals(100, OrthopedistRules.SPEED_TICKS);
        assertEquals(0.75D, OrthopedistRules.STAMINA_CONSUMPTION_MULTIPLIER, 0.000_001D);
    }

    @Test
    void targetDecisionRejectsRepeatHealsFractureOrAppliesBoneSetting() {
        assertEquals(OrthopedistRules.TargetAction.REJECT_ALREADY_ACTIVE,
                OrthopedistRules.targetAction(0, true));
        assertEquals(OrthopedistRules.TargetAction.HEAL_FRACTURE,
                OrthopedistRules.targetAction(1, false));
        assertEquals(OrthopedistRules.TargetAction.APPLY_BONE_SETTING,
                OrthopedistRules.targetAction(0, false));
    }

    @Test
    void serverValidationRequiresReadyLivingOrthopedistAndVisibleTargetInRange() {
        assertTrue(OrthopedistRules.canAttempt(true, true, false, 0, true, true, 9.0D));
        assertFalse(OrthopedistRules.canAttempt(false, true, false, 0, true, true, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, false, false, 0, true, true, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, true, true, 0, true, true, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, true, false, 1, true, true, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, true, false, 0, false, true, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, true, false, 0, true, false, 1.0D));
        assertFalse(OrthopedistRules.canAttempt(true, true, false, 0, true, true, Math.nextUp(9.0D)));
    }
}
