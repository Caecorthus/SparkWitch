package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsMovementRulesTest {
    @Test
    void serverFallbackOnlyMovesUnlockedPlayersWithoutClientChannel() {
        assertTrue(FocusedFootstepsMovementRules.shouldUseServerFallback(false, false, false));
        assertFalse(FocusedFootstepsMovementRules.shouldUseServerFallback(true, false, false));
        assertFalse(FocusedFootstepsMovementRules.shouldUseServerFallback(false, true, false));
        assertFalse(FocusedFootstepsMovementRules.shouldUseServerFallback(false, false, true));
    }

    @Test
    void hardMovementLocksAlsoSuppressForcedSprinting() {
        assertTrue(FocusedFootstepsMovementRules.shouldSprint(true, false, false));
        assertFalse(FocusedFootstepsMovementRules.shouldSprint(true, true, false));
        assertFalse(FocusedFootstepsMovementRules.shouldSprint(true, false, true));
        assertFalse(FocusedFootstepsMovementRules.shouldSprint(false, false, false));
    }
}
