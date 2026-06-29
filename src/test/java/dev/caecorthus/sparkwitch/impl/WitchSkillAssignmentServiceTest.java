package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillAssignmentServiceTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @AfterEach
    void clearRegistry() {
        WitchSkillRegistry.clearForTests();
    }

    @Test
    void legalForcedSkillWinsOverRandomPool() {
        WitchSkillDefinition forced = skill("forced", role -> role == SparkWitchRoles.grandWitch());
        WitchSkillDefinition random = skill("random", role -> role == SparkWitchRoles.grandWitch());
        WitchSkillRegistry.register(forced);

        WitchSkillAssignmentService.SkillAssignmentPlan plan = WitchSkillAssignmentService.selectSkillForRole(
                SparkWitchRoles.grandWitch(),
                forced.id(),
                List.of(random),
                context(SparkWitchRoles.grandWitch()),
                new Random(1)
        );

        assertEquals(forced.id(), plan.selected().orElseThrow().id());
        assertTrue(plan.consumedForcedSkill());
    }

    @Test
    void invalidForcedSkillIsConsumedAndFallsBackToRandomPool() {
        WitchSkillDefinition forced = skill("forced", role -> role == SparkWitchRoles.apprenticeWitch());
        WitchSkillDefinition random = skill("random", role -> role == SparkWitchRoles.grandWitch());
        WitchSkillRegistry.register(forced);

        WitchSkillAssignmentService.SkillAssignmentPlan plan = WitchSkillAssignmentService.selectSkillForRole(
                SparkWitchRoles.grandWitch(),
                forced.id(),
                List.of(random),
                context(SparkWitchRoles.grandWitch()),
                new Random(1)
        );

        assertEquals(random.id(), plan.selected().orElseThrow().id());
        assertTrue(plan.consumedForcedSkill());
    }

    @Test
    void nonSparkWitchRoleDoesNotReceiveForcedSkill() {
        WitchSkillDefinition forced = skill("forced", role -> true);
        WitchSkillRegistry.register(forced);

        WitchSkillAssignmentService.SkillAssignmentPlan plan = WitchSkillAssignmentService.selectSkillForRole(
                WatheRoles.CIVILIAN,
                forced.id(),
                List.of(forced),
                context(WatheRoles.CIVILIAN),
                new Random(1)
        );

        assertTrue(plan.selected().isEmpty());
        assertTrue(plan.consumedForcedSkill());
    }

    @Test
    void pigGodReceivesPigChaseThroughAssignmentPlan() {
        WitchSkillDefinition pigChase = skill("pig_chase", role -> role == SparkWitchRoles.pigGod());

        WitchSkillAssignmentService.SkillAssignmentPlan plan = WitchSkillAssignmentService.selectSkillForRole(
                SparkWitchRoles.pigGod(),
                null,
                List.of(pigChase),
                context(SparkWitchRoles.pigGod()),
                new Random(1)
        );

        assertEquals(pigChase.id(), plan.selected().orElseThrow().id());
    }

    @Test
    void duplicateSameSkillAssignmentPreservesCurrentCooldownState() {
        assertFalse(WitchSkillAssignmentService.shouldApplyInitialCooldown(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.PIG_CHASE_ID
        ));
        assertTrue(WitchSkillAssignmentService.shouldApplyInitialCooldown(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                PigGodRules.PIG_CHASE_ID
        ));
        assertTrue(WitchSkillAssignmentService.shouldApplyInitialCooldown(
                PigGodRules.PIG_CHASE_ID,
                null
        ));
    }

    private static WitchSkillSelectionContext context(dev.doctor4t.wathe.api.Role role) {
        return new WitchSkillSelectionContext(null, null, null, role);
    }

    private static WitchSkillDefinition skill(String path, RoleRule rule) {
        return new WitchSkillDefinition(
                SparkWitch.id(path),
                0xFFFFFF,
                1,
                20,
                context -> rule.canSelect(context.role()),
                null
        );
    }

    private interface RoleRule {
        boolean canSelect(dev.doctor4t.wathe.api.Role role);
    }
}
