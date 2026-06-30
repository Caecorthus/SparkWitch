package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillPresentationRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void pigGodPigChaseDoesNotShowInventorySkillPanel() {
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.pigGod(),
                PigGodRules.PIG_CHASE_ID
        ));
    }

    @Test
    void witchRolesWithSkillsStillShowInventorySkillPanel() {
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(),
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID
        ));
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(),
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID
        ));
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(),
                MurderousWitchDeathRayRules.DEATH_RAY_ID
        ));
    }

    @Test
    void missingOrNonWitchSkillsDoNotShowInventorySkillPanel() {
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(),
                null
        ));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                WatheRoles.CIVILIAN,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID
        ));
    }
}
