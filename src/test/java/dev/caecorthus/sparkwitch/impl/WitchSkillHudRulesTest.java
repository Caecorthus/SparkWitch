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

    @Test
    void manaSkillsShowManaRequirementOnlyWhenReadyAndUnaffordable() {
        assertTrue(WitchSkillHudRules.shouldShowManaRequirement(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST - 1,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST - 1,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                1,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST - 1,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                0,
                1
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                SparkWitch.id("free_skill"),
                0,
                0,
                0,
                0
        ));
    }

    @Test
    void ceremonialSwordShowsTaskUnlockBeforeManaRequirement() {
        assertTrue(WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS - 1,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                GrandWitchRules.CEREMONIAL_SWORD_MANA_COST - 1,
                GrandWitchRules.CEREMONIAL_SWORD_MANA_COST,
                0,
                0,
                GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS - 1
        ));
        assertFalse(WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                SparkWitch.id("mighty_force"),
                0,
                0,
                0
        ));
    }
}
