package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithParticipationRulesTest {
    @Test
    void onlyActiveWraithsOptIntoWatheChatAllowance() {
        assertTrue(WraithParticipationRules.mayUseTextChat(true));
        assertFalse(WraithParticipationRules.mayUseTextChat(false));
    }

    @Test
    void activeWraithCannotBypassMapJumpRestriction() {
        assertFalse(WraithParticipationRules.mayJump(true, false));
        assertTrue(WraithParticipationRules.mayJump(true, true));
        assertTrue(WraithParticipationRules.mayJump(false, false));
        assertTrue(WraithParticipationRules.mayJump(false, true));
    }

    @Test
    void activeWraithDoesNotGenerateOrdinaryGroundParticles() {
        assertFalse(WraithParticipationRules.mayGenerateGroundParticles(true));
        assertTrue(WraithParticipationRules.mayGenerateGroundParticles(false));
    }
}
