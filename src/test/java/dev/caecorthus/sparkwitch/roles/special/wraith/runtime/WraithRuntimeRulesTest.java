package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRuntimeRulesTest {
    @Test
    void playerIsolationIsBilateralExceptForSelfActions() {
        assertTrue(WraithParticipation.canAffectPlayer(false, false, false));
        assertTrue(WraithParticipation.canAffectPlayer(true, true, true));
        assertFalse(WraithParticipation.canAffectPlayer(true, false, false));
        assertFalse(WraithParticipation.canAffectPlayer(false, true, false));
    }

    @Test
    void restrictedFactionUsesOnlyTheSavedAlignment() {
        assertEquals(
                dev.caecorthus.sparkfactionapi.api.FactionIds.CIVILIAN,
                WraithParticipation.restrictedFaction(true, WraithState.Alignment.GOOD)
        );
        assertEquals(
                dev.caecorthus.sparkfactionapi.api.FactionIds.KILLER,
                WraithParticipation.restrictedFaction(true, WraithState.Alignment.KILLER)
        );
        assertNull(WraithParticipation.restrictedFaction(false, WraithState.Alignment.KILLER));
    }

    @Test
    void fallAndReconnectRulesKeepServerAuthority() {
        assertTrue(WraithLifecycle.shouldTerminateForFall(true, -0.01D, 0.0D));
        assertFalse(WraithLifecycle.shouldTerminateForFall(true, 0.0D, 0.0D));
        assertTrue(WraithLifecycle.shouldResume(true, true, true, true));
        assertFalse(WraithLifecycle.shouldResume(true, true, true, false));
    }
}
