package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillUseReadinessTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void rejectsMissingActiveSkill() {
        WitchSkillUseReadiness.Result result = WitchSkillUseReadiness.check(
                SparkWitchRoles.apprenticeWitch(),
                state(0, 0, false, false, 0),
                null,
                null
        );

        assertFalse(result.accepted());
        assertEquals("message.sparkwitch.skill.no_skill", result.messageKey());
    }

    @Test
    void rejectsLockedGrandWitchCeremonialSwordWithProgressArgs() {
        WitchSkillUseReadiness.Result result = WitchSkillUseReadiness.check(
                SparkWitchRoles.grandWitch(),
                state(0, 0, false, false, 0),
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                ceremonialSwordDefinition()
        );

        assertFalse(result.accepted());
        assertEquals("message.sparkwitch.skill.ceremonial_sword.locked", result.messageKey());
        assertArrayEquals(new Object[]{0, WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS}, result.messageArgs());
    }

    @Test
    void cooldownUsesCeilingSeconds() {
        Identifier skillId = SparkWitch.id("test_skill");

        WitchSkillUseReadiness.Result result = WitchSkillUseReadiness.check(
                SparkWitchRoles.apprenticeWitch(),
                state(21, 0, false, false, 0),
                skillId,
                testDefinition(skillId)
        );

        assertFalse(result.accepted());
        assertEquals("message.sparkwitch.skill.cooldown", result.messageKey());
        assertArrayEquals(new Object[]{2.0}, result.messageArgs());
    }

    @Test
    void unknownSkillRequestsComponentClear() {
        Identifier skillId = SparkWitch.id("missing_skill");

        WitchSkillUseReadiness.Result result = WitchSkillUseReadiness.check(
                SparkWitchRoles.apprenticeWitch(),
                state(0, 0, false, false, 0),
                skillId,
                null
        );

        assertFalse(result.accepted());
        assertTrue(result.clearComponent());
        assertEquals("message.sparkwitch.skill.unknown", result.messageKey());
    }

    private static WitchSkillUseReadiness.State state(
            int cooldownTicks,
            int activeSkillWindowTicks,
            boolean hasDeferredCooldown,
            boolean hasUnlockedGrandWitchCeremonialSword,
            int grandWitchCeremonialSwordTasks
    ) {
        return new WitchSkillUseReadiness.State(
                cooldownTicks,
                activeSkillWindowTicks,
                hasDeferredCooldown,
                hasUnlockedGrandWitchCeremonialSword,
                grandWitchCeremonialSwordTasks
        );
    }

    private static WitchSkillDefinition ceremonialSwordDefinition() {
        return testDefinition(GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID);
    }

    private static WitchSkillDefinition testDefinition(Identifier skillId) {
        return new WitchSkillDefinition(
                skillId,
                0xFFFFFF,
                1,
                0,
                20,
                0,
                context -> true,
                context -> dev.caecorthus.sparkwitch.api.WitchSkillUseResult.success(0)
        );
    }
}
