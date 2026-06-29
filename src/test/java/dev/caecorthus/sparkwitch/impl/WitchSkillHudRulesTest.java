package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillHudRulesTest {
    @Test
    void pigChaseShowsCoinRequirementOnlyWhenReadyAndUnaffordable() {
        assertTrue(WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.COIN_COST - 1,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.COIN_COST,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.COIN_COST - 1,
                1,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.COIN_COST - 1,
                0,
                1
        ));
        assertFalse(WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                SparkWitch.id("mighty_force"),
                PigGodRules.COIN_COST - 1,
                0,
                0
        ));
    }
}
