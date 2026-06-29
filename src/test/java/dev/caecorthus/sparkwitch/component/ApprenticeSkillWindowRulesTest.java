package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.impl.ApprenticeWitchSkillRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeSkillWindowRulesTest {
    @Test
    void clairvoyanceEffectiveWindowUsesOnlySharedHighlightTicks() {
        int activeTicks = ApprenticeSkillWindowRules.effectiveWindowTicks(
                0,
                0,
                0,
                0,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_TICKS,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_OTHERS_TICKS
        );

        assertEquals(ApprenticeWitchSkillRules.CLAIRVOYANCE_OTHERS_TICKS, activeTicks);
    }

    @Test
    void clairvoyanceSelfGlowDoesNotExtendEffectiveWindow() {
        int activeTicks = ApprenticeSkillWindowRules.effectiveWindowTicks(
                0,
                0,
                0,
                0,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_TICKS,
                0
        );

        assertEquals(0, activeTicks);
    }

    @Test
    void deferredCooldownStartsOnlyWhenEffectiveWindowEnds() {
        assertTrue(ApprenticeSkillWindowRules.shouldStartDeferredCooldown(
                1,
                0,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_COOLDOWN_TICKS
        ));
        assertFalse(ApprenticeSkillWindowRules.shouldStartDeferredCooldown(
                1,
                1,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_COOLDOWN_TICKS
        ));
        assertFalse(ApprenticeSkillWindowRules.shouldStartDeferredCooldown(0, 0, 0));
    }
}
