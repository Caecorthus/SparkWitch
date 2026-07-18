package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithPlayerStateTest {
    @Test
    void activationPromotionAndClearFormOneStateMachine() {
        WraithPlayerState state = new WraithPlayerState();
        state.activate(WraithState.Alignment.WITCH);
        assertTrue(state.isActive());
        assertTrue(state.isRestricted());
        assertEquals(WraithState.Alignment.WITCH, state.getAlignment());

        state.recordTaskCompletion();
        state.setPromotionPending(true);
        assertTrue(state.promote());
        assertTrue(state.isPromoted());
        assertFalse(state.isPromotionPending());

        assertTrue(state.clear());
        assertFalse(state.isActive());
        assertEquals(0, state.getCompletedTasks());
        assertNull(state.getAlignment());
    }

    @Test
    void publicRecipientCanRetainActiveFlagWithoutPrivateAlignment() {
        WraithPlayerState state = new WraithPlayerState();
        state.restore(true, true, 0, null, false);
        assertTrue(state.isActive());
        assertTrue(state.isRestricted());
        assertNull(state.getAlignment());
    }
}
