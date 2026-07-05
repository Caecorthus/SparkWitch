package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
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
                MightyForceAbility.ID,
                MightyForceAbility.MANA_COST - 1,
                MightyForceAbility.MANA_COST,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                MightyForceAbility.ID,
                MightyForceAbility.MANA_COST,
                MightyForceAbility.MANA_COST,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                MightyForceAbility.ID,
                MightyForceAbility.MANA_COST - 1,
                MightyForceAbility.MANA_COST,
                1,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                MightyForceAbility.ID,
                MightyForceAbility.MANA_COST - 1,
                MightyForceAbility.MANA_COST,
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
                WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS - 1,
                0,
                0
        ));
        assertFalse(WitchSkillHudRules.shouldShowManaRequirement(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                WitchFactionRules.CEREMONIAL_SWORD_MANA_COST - 1,
                WitchFactionRules.CEREMONIAL_SWORD_MANA_COST,
                0,
                0,
                WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS - 1
        ));
        assertFalse(WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS,
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
