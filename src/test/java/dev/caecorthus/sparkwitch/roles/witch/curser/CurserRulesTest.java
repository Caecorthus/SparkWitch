package dev.caecorthus.sparkwitch.roles.witch.curser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurserRulesTest {
    @Test
    void playerOutlineRequiresEveryServerRoundRoleWraithAndViewerGate() {
        assertTrue(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(false, true, true, true, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, false, true, true, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, false, true, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, false, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, false, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, true, true, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, true, false, true));
    }

    @Test
    void instinctRequiresRunningActivePromotedCurserWithoutConfusion() {
        assertTrue(CurserRules.canUseInstinct(true, true, true, true, false));
        assertFalse(CurserRules.canUseInstinct(false, true, true, true, false));
        assertFalse(CurserRules.canUseInstinct(true, false, true, true, false));
        assertFalse(CurserRules.canUseInstinct(true, true, false, true, false));
        assertFalse(CurserRules.canUseInstinct(true, true, true, false, false));
        assertFalse(CurserRules.canUseInstinct(true, true, true, true, true));
    }

    @Test
    void clientUseAlsoRequiresConfirmedChannel() {
        assertTrue(CurserRules.canSend(true, true, true, true, true, 0, true));
        assertFalse(CurserRules.canSend(false, true, true, true, true, 0, true));
        assertFalse(CurserRules.canSend(true, false, true, true, true, 0, true));
        assertFalse(CurserRules.canSend(true, true, false, true, true, 0, true));
        assertFalse(CurserRules.canSend(true, true, true, false, true, 0, true));
        assertFalse(CurserRules.canSend(true, true, true, true, false, 0, true));
        assertFalse(CurserRules.canSend(true, true, true, true, true, 1, true));
        assertFalse(CurserRules.canSend(true, true, true, true, true, 0, false));
    }

    @Test
    void useRequiresPromotedActiveCurserAndReadyCooldown() {
        assertTrue(CurserRules.canUse(true, true, true, true, 0));
        assertFalse(CurserRules.canUse(false, true, true, true, 0));
        assertFalse(CurserRules.canUse(true, false, true, true, 0));
        assertFalse(CurserRules.canUse(true, true, false, true, 0));
        assertFalse(CurserRules.canUse(true, true, true, false, 0));
        assertFalse(CurserRules.canUse(true, true, true, true, 1));
    }
}
