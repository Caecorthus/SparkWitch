package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsRulesTest {
    private static final UUID CASTER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void exposesTheApprovedSkillTimingAndColor() {
        assertEquals("sparkwitch:focused_footsteps", FocusedFootstepsRules.SKILL_ID.toString());
        assertEquals(0xB04A8B, FocusedFootstepsRules.COLOR);
        assertEquals(1_200, FocusedFootstepsRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(600, FocusedFootstepsRules.EFFECT_TICKS);
        assertEquals(1_800, FocusedFootstepsRules.COOLDOWN_TICKS);
        assertEquals(1_200, FocusedFootstepsRules.COOLDOWN_AFTER_EFFECT_TICKS);
    }

    @Test
    void serverTargetMustBeAnotherOnlineLivingMatchPlayerInTheSameWorld() {
        assertTrue(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, true, true, true, false, true));

        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, CASTER, true, true, true, false, true));
        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, false, true, true, false, true));
        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, true, false, true, false, true));
        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, true, true, false, false, true));
        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, true, true, true, true, true));
        assertFalse(FocusedFootstepsRules.isValidTarget(
                CASTER, TARGET, true, true, true, false, false));
    }

    @Test
    void exhaustionPermanentlyTransitionsThisEffectFromRunningToWalking() {
        assertEquals(FocusedFootstepsRules.Phase.RUNNING,
                FocusedFootstepsRules.initialPhase(false, false));
        assertEquals(FocusedFootstepsRules.Phase.WALKING,
                FocusedFootstepsRules.initialPhase(false, true));
        assertEquals(FocusedFootstepsRules.Phase.RUNNING,
                FocusedFootstepsRules.initialPhase(true, true));

        assertEquals(FocusedFootstepsRules.Phase.WALKING,
                FocusedFootstepsRules.nextPhase(FocusedFootstepsRules.Phase.RUNNING, false, true));
        assertEquals(FocusedFootstepsRules.Phase.WALKING,
                FocusedFootstepsRules.nextPhase(FocusedFootstepsRules.Phase.WALKING, false, false));
        assertEquals(FocusedFootstepsRules.Phase.RUNNING,
                FocusedFootstepsRules.nextPhase(FocusedFootstepsRules.Phase.RUNNING, true, true));
    }

    @Test
    void clientOnlyEffectTimeRequiresTheSuccessfulCooldownJump() {
        assertTrue(FocusedFootstepsRules.confirmsSuccessfulUse(0, 1_800));
        assertFalse(FocusedFootstepsRules.confirmsSuccessfulUse(1, 1_800));
        assertFalse(FocusedFootstepsRules.confirmsSuccessfulUse(0, 1_200));
        assertFalse(FocusedFootstepsRules.confirmsSuccessfulUse(0, 0));

        assertEquals(600, FocusedFootstepsRules.effectTicksFromCooldown(1_800));
        assertEquals(1, FocusedFootstepsRules.effectTicksFromCooldown(1_201));
        assertEquals(0, FocusedFootstepsRules.effectTicksFromCooldown(1_200));
        assertEquals(0, FocusedFootstepsRules.effectTicksFromCooldown(-1));
    }
}
