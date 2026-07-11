package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeAbilityWindowRulesTest {
    @Test
    void tickPreservesWindowOrderAndStartsCooldownAtTheEffectiveBoundary() {
        ApprenticeAbilityWindowRules.WindowState state = new ApprenticeAbilityWindowRules.WindowState(
                1,
                21,
                0,
                0,
                0,
                2,
                1,
                120
        );

        ApprenticeAbilityWindowRules.TickResult result = ApprenticeAbilityWindowRules.tick(state);

        assertEquals(new ApprenticeAbilityWindowRules.WindowState(
                0,
                20,
                0,
                0,
                0,
                1,
                0,
                120
        ), result.state());
        assertFalse(result.healingPulseDue());
        assertFalse(result.startDeferredCooldown());
        assertTrue(result.syncRequired());
    }

    @Test
    void healingPulseFiresEveryTwentyTicksWithoutChangingTheWindowDuration() {
        ApprenticeAbilityWindowRules.WindowState state = new ApprenticeAbilityWindowRules.WindowState(
                0,
                0,
                0,
                5,
                19,
                0,
                0,
                120
        );

        ApprenticeAbilityWindowRules.TickResult result = ApprenticeAbilityWindowRules.tick(state);

        assertEquals(4, result.state().healingTicks());
        assertEquals(0, result.state().healingPulseTicks());
        assertTrue(result.healingPulseDue());
        assertFalse(result.startDeferredCooldown());
    }

    @Test
    void finalEffectiveTickStartsDeferredCooldownButSelfRevealDoesNotDelayIt() {
        ApprenticeAbilityWindowRules.WindowState state = new ApprenticeAbilityWindowRules.WindowState(
                0,
                0,
                0,
                0,
                0,
                10,
                1,
                120
        );

        ApprenticeAbilityWindowRules.TickResult result = ApprenticeAbilityWindowRules.tick(state);

        assertEquals(9, result.state().clairvoyanceSelfTicks());
        assertEquals(0, result.state().clairvoyanceOthersTicks());
        assertTrue(result.startDeferredCooldown());
        assertTrue(result.syncRequired());
    }
}
