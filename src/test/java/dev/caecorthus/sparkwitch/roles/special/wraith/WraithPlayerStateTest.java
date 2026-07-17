package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithPlayerStateTest {
    @Test
    void ownsTheRestrictedToPromotedStateMachine() {
        WraithPlayerState state = new WraithPlayerState();

        state.activate(WraithState.Alignment.GOOD);
        assertTrue(state.isActive());
        assertTrue(state.isRestricted());
        assertEquals(1, state.recordTaskCompletion());
        assertTrue(state.setPromotionPending(true));
        assertTrue(state.isPromotionPending());

        assertTrue(state.promote());
        assertTrue(state.isActive());
        assertFalse(state.isRestricted());
        assertFalse(state.isPromotionPending());
        assertEquals(WraithState.Alignment.GOOD, state.getAlignment());

        assertTrue(state.clear());
        assertFalse(state.isActive());
        assertEquals(0, state.getCompletedTasks());
    }

    @Test
    void restoresOnlyActiveWraithState() {
        WraithPlayerState state = new WraithPlayerState();

        state.restore(true, true, 1, WraithState.Alignment.KILLER, true);
        assertTrue(state.isRestricted());
        assertEquals(1, state.getCompletedTasks());
        assertEquals(WraithState.Alignment.KILLER, state.getAlignment());
        assertTrue(state.isPromotionPending());

        state.restore(false, true, 7, WraithState.Alignment.GOOD, true);
        assertFalse(state.isActive());
        assertFalse(state.isRestricted());
        assertEquals(0, state.getCompletedTasks());
        assertNull(state.getAlignment());
        assertFalse(state.isPromotionPending());
    }
}
