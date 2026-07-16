package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlackRavenRulesTest {
    @Test
    void freezesApprovedGameplayValues() {
        assertEquals("sparkwitch:black_raven", BlackRavenRules.ROLE_ID.toString());
        assertEquals("sparkwitch:perception", BlackRavenRules.PERCEPTION_SKILL_ID.toString());
        assertEquals(0x51445F, BlackRavenRules.COLOR);
        assertEquals(3.0D, BlackRavenRules.FEATHER_REACH);
        assertEquals(400, BlackRavenRules.MARK_DURATION_TICKS);
        assertEquals(1200, BlackRavenRules.FEATHER_COOLDOWN_TICKS);
        assertEquals(1200, BlackRavenRules.PERCEPTION_INITIAL_COOLDOWN_TICKS);
        assertEquals(300, BlackRavenRules.PERCEPTION_ACTIVE_TICKS);
        assertEquals(1800, BlackRavenRules.PERCEPTION_COOLDOWN_TICKS);
        assertEquals(64.0D, BlackRavenRules.PERCEPTION_RADIUS_SQUARED);
        assertEquals(20, BlackRavenRules.PERCEPTION_POINT_TICKS);
        assertEquals(10, BlackRavenRules.PERCEPTION_REVEAL_POINTS);
    }

    @Test
    void featherTargetGateRejectsEveryNonTargetCase() {
        assertTrue(BlackRavenRules.canMark(true, true, true, false, false, true, 9.0D));
        assertFalse(BlackRavenRules.canMark(false, true, true, false, false, true, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, false, true, false, false, true, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, true, false, false, false, true, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, true, true, true, false, true, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, true, true, false, true, true, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, true, true, false, false, false, 1.0D));
        assertFalse(BlackRavenRules.canMark(true, true, true, false, false, true, 9.0001D));
    }

    @Test
    void perceptionUsesInclusiveThreeDimensionalRadius() {
        assertTrue(BlackRavenRules.isWithinPerceptionRadius(64.0D));
        assertFalse(BlackRavenRules.isWithinPerceptionRadius(64.0001D));
    }

    @Test
    void inactivePerceptionStateSurvivesOnlyForTheSameMatchAndExactRole() {
        UUID storedMatch = UUID.randomUUID();

        assertTrue(BlackRavenRules.shouldPreservePerceptionRoundState(storedMatch, storedMatch, true));
        assertFalse(BlackRavenRules.shouldPreservePerceptionRoundState(storedMatch, null, true));
        assertFalse(BlackRavenRules.shouldPreservePerceptionRoundState(
                storedMatch,
                UUID.randomUUID(),
                true
        ));
        assertFalse(BlackRavenRules.shouldPreservePerceptionRoundState(storedMatch, storedMatch, false));
        assertFalse(BlackRavenRules.shouldPreservePerceptionRoundState(null, storedMatch, true));
    }
}
