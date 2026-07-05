package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeAbilityWindowRulesTest {
    @Test
    void clairvoyanceEffectiveWindowUsesOnlySharedHighlightTicks() {
        int activeTicks = ApprenticeAbilityWindowRules.effectiveWindowTicks(
                0,
                0,
                0,
                0,
                ClairvoyanceAbility.SELF_TICKS,
                ClairvoyanceAbility.OTHERS_TICKS
        );

        assertEquals(ClairvoyanceAbility.OTHERS_TICKS, activeTicks);
    }

    @Test
    void clairvoyanceSelfGlowDoesNotExtendEffectiveWindow() {
        int activeTicks = ApprenticeAbilityWindowRules.effectiveWindowTicks(
                0,
                0,
                0,
                0,
                ClairvoyanceAbility.SELF_TICKS,
                0
        );

        assertEquals(0, activeTicks);
    }

    @Test
    void deferredCooldownStartsOnlyWhenEffectiveWindowEnds() {
        assertTrue(ApprenticeAbilityWindowRules.shouldStartDeferredCooldown(
                1,
                0,
                ClairvoyanceAbility.COOLDOWN_TICKS
        ));
        assertFalse(ApprenticeAbilityWindowRules.shouldStartDeferredCooldown(
                1,
                1,
                ClairvoyanceAbility.COOLDOWN_TICKS
        ));
        assertFalse(ApprenticeAbilityWindowRules.shouldStartDeferredCooldown(0, 0, 0));
    }
}
