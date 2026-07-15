package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintKarmaStateTest {
    private static final UUID PLAYER = UUID.fromString("73f801dd-2e8f-4fb6-97b1-996739f7301c");

    @Test
    void unmarkedPlayersCannotTriggerKarma() {
        SaintKarmaState state = new SaintKarmaState();

        assertEquals(0, state.trigger(PLAYER, 100));
        assertFalse(state.isMarked(PLAYER));
    }

    @Test
    void markSurvivesCooldownExpiryUntilRoundClear() {
        SaintKarmaState state = new SaintKarmaState();

        assertTrue(state.mark(PLAYER));
        assertEquals(100, state.trigger(PLAYER, 100));
        for (int tick = 0; tick < 100; tick++) {
            state.tick();
        }

        assertTrue(state.isMarked(PLAYER));
        assertEquals(0, state.remainingTicks(PLAYER));

        state.clear();
        assertFalse(state.isMarked(PLAYER));
    }

    @Test
    void repeatedTriggersRefreshWithoutStackingOrShortening() {
        SaintKarmaState state = new SaintKarmaState();
        state.mark(PLAYER);

        assertEquals(100, state.trigger(PLAYER, 100));
        for (int tick = 0; tick < 40; tick++) {
            state.tick();
        }
        assertEquals(100, state.trigger(PLAYER, 100));
        assertEquals(400, state.trigger(PLAYER, 400));
        assertEquals(400, state.trigger(PLAYER, 100));
    }

    @Test
    void restoredEntriesRemainMarkedEvenWithNoActiveCooldown() {
        SaintKarmaState state = new SaintKarmaState();

        state.restore(PLAYER, -10);

        assertTrue(state.isMarked(PLAYER));
        assertEquals(0, state.remainingTicks(PLAYER));
        assertEquals(1, state.entries().size());
        assertEquals(PLAYER, state.entries().getFirst().playerUuid());
    }
}
