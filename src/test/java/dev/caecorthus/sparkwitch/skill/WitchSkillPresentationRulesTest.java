package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillPresentationRulesTest {
    private static final Identifier UNRELATED_SKILL = Identifier.of("sparkwitch", "unrelated_skill");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void approvedWitchRoleSkillsShowInventorySkillPanel() {
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(),
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID
        ));
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(),
                MightyForceAbility.ID
        ));
        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(),
                MurderousWitchDeathRayRules.DEATH_RAY_ID
        ));
    }

    @Test
    void allOtherRolesStayOutOfInventorySkillPanel() {
        List<Role> excludedRoles = List.of(
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.pigGod(),
                SparkWitchRoles.prophet(),
                SparkWitchRoles.saint(),
                SparkWitchRoles.perfumer(),
                SparkWitchRoles.tarotReader(),
                SparkWitchRoles.ninja(),
                SparkWitchRoles.kidnapper(),
                SparkWitchRoles.blackRaven(),
                SparkWitchRoles.witchMaiden(),
                SparkWitchRoles.hunter(),
                SparkWitchRoles.orthopedist()
        );

        for (Role role : excludedRoles) {
            assertFalse(
                    WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, UNRELATED_SKILL),
                    () -> role.identifier() + " must not use the Witch skill panel"
            );
        }
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.witchMaiden(),
                WitchMaidenRules.FOCUSED_FOOTSTEPS_SKILL_ID
        ));
    }

    @Test
    void approvedRolesStillRejectUnrelatedOrMissingSkills() {
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(),
                UNRELATED_SKILL
        ));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(),
                UNRELATED_SKILL
        ));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(),
                UNRELATED_SKILL
        ));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(null, UNRELATED_SKILL));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(),
                null
        ));
    }
}
