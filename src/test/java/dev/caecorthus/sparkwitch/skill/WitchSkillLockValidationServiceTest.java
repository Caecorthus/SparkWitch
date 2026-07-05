package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


class WitchSkillLockValidationServiceTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @BeforeEach
    void registerSkills() {
        WitchSkillRegistry.clearForTests();
        SparkWitchBuiltInSkills.resetForTests();
        SparkWitchBuiltInSkills.register();
    }

    @AfterEach
    void clearSkills() {
        WitchSkillRegistry.clearForTests();
        SparkWitchBuiltInSkills.resetForTests();
    }

    @Test
    void grandWitchSkillAllowsGrandWitchAndRejectsApprenticeWitch() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID);

        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.grandWitch()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void apprenticeSkillAllowsApprenticeWitchAndRejectsGrandWitch() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(MightyForceAbility.ID);

        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.apprenticeWitch()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.grandWitch()));
    }

    @Test
    void pigChaseAllowsPigGodOnly() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(PigGodRules.PIG_CHASE_ID);

        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.pigGod()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.grandWitch()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void deathRayAllowsMurderousWitchOnly() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(MurderousWitchDeathRayRules.DEATH_RAY_ID);

        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.murderousWitch()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.grandWitch()));
        assertNotNull(WitchSkillLockValidationService.findRoleConflict(skill, SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void pendingForcedSkillBlocksIncompatibleForcedRoleLater() {
        WitchSkillLockValidationService.RoleConflict conflict =
                WitchSkillLockValidationService.findForcedSkillRoleConflict(
                        MightyForceAbility.ID,
                        SparkWitchRoles.grandWitch()
                );

        assertNotNull(conflict);
        assertEquals(MightyForceAbility.ID, conflict.skill().id());
        assertEquals(SparkWitchRoles.grandWitch(), conflict.role());
    }

    @Test
    void unknownAndNoRoleDoNotCreateCommandTimeConflict() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(MightyForceAbility.ID);

        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, null));
        assertNull(WitchSkillLockValidationService.findRoleConflict(skill, WatheRoles.NO_ROLE));
    }

    @Test
    void conflictMessageNamesShortSkillAndRoleIds() {
        WitchSkillDefinition skill = WitchSkillRegistry.get(MightyForceAbility.ID);
        WitchSkillLockValidationService.RoleConflict conflict =
                new WitchSkillLockValidationService.RoleConflict(skill, SparkWitchRoles.grandWitch());

        assertEquals(
                "无法锁定，因为 mighty_force 与 grand_witch 冲突。",
                WitchSkillLockValidationService.forceAbilityConflictMessage(conflict)
        );
    }
}
