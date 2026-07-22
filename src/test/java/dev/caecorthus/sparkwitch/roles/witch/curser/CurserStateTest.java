package dev.caecorthus.sparkwitch.roles.witch.curser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurserStateTest {
    @Test
    void promotionStartsAtSixtySecondCooldown() {
        CurserState state = new CurserState();
        state.initializeForPromotion();
        assertEquals(CurserRules.INITIAL_COOLDOWN_TICKS, state.cooldownTicks());
        assertFalse(state.startCooldown());
    }

    @Test
    void successfulUseStartsNinetySecondCooldownAndConfusionLastsTenSeconds() {
        CurserState caster = new CurserState();
        CurserState recipient = new CurserState();
        assertTrue(caster.startCooldown());
        recipient.applyConfusion();
        assertEquals(CurserRules.COOLDOWN_TICKS, caster.cooldownTicks());
        assertEquals(CurserRules.CONFUSION_TICKS, recipient.confusionTicks());
        for (int tick = 0; tick < CurserRules.CONFUSION_TICKS; tick++) {
            recipient.tickConfusion();
        }
        assertFalse(recipient.isConfused());
    }

    @Test
    void cleanupClearsBothPrivateStates() {
        CurserState state = new CurserState();
        state.startCooldown();
        state.applyConfusion();
        assertTrue(state.clear());
        assertEquals(0, state.cooldownTicks());
        assertEquals(0, state.confusionTicks());
    }
}
