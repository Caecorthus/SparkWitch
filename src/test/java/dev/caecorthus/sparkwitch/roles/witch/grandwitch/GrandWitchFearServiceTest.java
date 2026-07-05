package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchFearServiceTest {
    @Test
    void fearPulsesTenTimesForFiftyTotalSanityOverTenSeconds() {
        int durationTicks = WitchFactionRules.GrandWitchSpell.FEAR.durationTicks();

        assertEquals(10, GrandWitchFearService.pulseCount(durationTicks));
        assertEquals(0.05f, GrandWitchFearService.moodLossPerPulse(durationTicks), 0.0001f);
        assertEquals(0.5f, GrandWitchFearService.moodLossPerPulse(durationTicks)
                * GrandWitchFearService.pulseCount(durationTicks), 0.0001f);
        assertTrue(GrandWitchFearService.shouldPulseFear(200));
        assertFalse(GrandWitchFearService.shouldPulseFear(199));
        assertTrue(GrandWitchFearService.shouldPulseFear(20));
        assertFalse(GrandWitchFearService.shouldPulseFear(0));
    }

    @Test
    void blockedRoleSkillPayloadsCoverSparkWitchAndNoellesRoleAbilitiesOnly() {
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(SparkWitch.id("use_skill")));
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(SparkWitch.id("fire_death_ray")));
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(Identifier.of("noellesroles", "ability")));
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(Identifier.of("noellesroles", "assassin_guess_role")));
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(Identifier.of("noellesroles", "demon_hunter_shoot")));

        assertFalse(GrandWitchFearService.isBlockedRoleSkillPayload(Identifier.of("wathe", "storebuy")));
        assertFalse(GrandWitchFearService.isBlockedRoleSkillPayload(Identifier.of("wathe", "map_vote")));
        assertFalse(GrandWitchFearService.isBlockedRoleSkillPayload(null));
    }
}
