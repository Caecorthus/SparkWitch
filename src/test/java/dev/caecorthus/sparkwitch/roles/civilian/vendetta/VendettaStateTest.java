package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaStateTest {
    @Test
    void stagedKillerRemainsDormantUntilVendettaPromotion() {
        VendettaState state = new VendettaState();
        UUID killerUuid = UUID.randomUUID();

        state.stageCreditedKiller(killerUuid);

        assertEquals(killerUuid, state.boundKillerUuid());
        assertFalse(state.isActive());
        assertTrue(state.activate());
        assertTrue(state.isActive());
        assertEquals(VendettaRules.REVEAL_COOLDOWN_TICKS, state.revealCooldownTicks());
        assertEquals(0, state.revealActiveTicks());
        assertTrue(state.isKnifeAvailable());
    }

    @Test
    void timerPausesUntilBothPlayersAreOnlineAndAlternatesThirtyAndFiveSeconds() {
        VendettaState state = activeState();

        assertFalse(state.tickTimer(false));
        assertEquals(VendettaRules.REVEAL_COOLDOWN_TICKS, state.revealCooldownTicks());
        assertTrue(state.isTimerPaused());

        tick(state, VendettaRules.REVEAL_COOLDOWN_TICKS, true);
        assertEquals(0, state.revealCooldownTicks());
        assertEquals(VendettaRules.REVEAL_DURATION_TICKS, state.revealActiveTicks());
        assertFalse(state.isTimerPaused());

        tick(state, VendettaRules.REVEAL_DURATION_TICKS, true);
        assertEquals(VendettaRules.REVEAL_COOLDOWN_TICKS, state.revealCooldownTicks());
        assertEquals(0, state.revealActiveTicks());
    }

    @Test
    void restoreAndClearPreserveOnlyNormalizedPersistentState() {
        VendettaState state = new VendettaState();
        UUID killerUuid = UUID.randomUUID();

        state.restore(killerUuid, true, -10, 140);

        assertEquals(killerUuid, state.boundKillerUuid());
        assertTrue(state.isActive());
        assertEquals(0, state.revealCooldownTicks());
        assertEquals(VendettaRules.REVEAL_DURATION_TICKS, state.revealActiveTicks());
        assertTrue(state.isKnifeAvailable());
        assertTrue(state.consumeKnife());
        assertFalse(state.isKnifeAvailable());
        assertFalse(state.consumeKnife());
        assertTrue(state.clear());
        assertNull(state.boundKillerUuid());
        assertFalse(state.isActive());
        assertFalse(state.clear());
    }

    private static VendettaState activeState() {
        VendettaState state = new VendettaState();
        state.stageCreditedKiller(UUID.randomUUID());
        assertTrue(state.activate());
        return state;
    }

    private static void tick(VendettaState state, int ticks, boolean bothOnline) {
        for (int index = 0; index < ticks; index++) {
            state.tickTimer(bothOnline);
        }
    }
}
