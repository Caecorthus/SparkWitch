package dev.caecorthus.sparkwitch.roles.witch.curser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurserRulesTest {
    @Test
    void playerOutlineRequiresConfirmedExactActivePromotedCurserOutsideSpectatorAndCreative() {
        assertTrue(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(false, true, true, true, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, false, true, true, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, false, true, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, false, false));
        assertFalse(CurserRules.canParticipateInPlayerOutlines(true, true, true, true, true));
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
