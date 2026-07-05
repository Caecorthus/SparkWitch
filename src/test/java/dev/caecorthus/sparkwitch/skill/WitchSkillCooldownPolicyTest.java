package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillCooldownPolicyTest {
    @Test
    void finalCooldownUsesHigherDefinitionOrResultCooldown() {
        WitchSkillDefinition skill = definition(100);

        WitchSkillCooldownPolicy.Decision definitionWins =
                WitchSkillCooldownPolicy.decide(skill, WitchSkillUseResult.success(20));
        WitchSkillCooldownPolicy.Decision resultWins =
                WitchSkillCooldownPolicy.decide(skill, WitchSkillUseResult.success(140));

        assertEquals(100, definitionWins.cooldownTicks());
        assertEquals(140, resultWins.cooldownTicks());
    }

    @Test
    void preservesDeferredCooldownChoice() {
        WitchSkillCooldownPolicy.Decision decision = WitchSkillCooldownPolicy.decide(
                definition(20),
                WitchSkillUseResult.successAfterActiveWindow(40, "message.sparkwitch.test")
        );

        assertEquals(40, decision.cooldownTicks());
        assertTrue(decision.deferUntilActiveWindowEnds());
    }

    @Test
    void decisionClampsNegativeCooldown() {
        WitchSkillCooldownPolicy.Decision decision = new WitchSkillCooldownPolicy.Decision(-1, true);

        assertEquals(0, decision.cooldownTicks());
        assertTrue(decision.deferUntilActiveWindowEnds());
    }

    private static WitchSkillDefinition definition(int cooldownTicks) {
        return new WitchSkillDefinition(
                SparkWitch.id("cooldown_policy_test"),
                0xFFFFFF,
                1,
                0,
                cooldownTicks,
                0,
                context -> true,
                context -> WitchSkillUseResult.success(0)
        );
    }
}
