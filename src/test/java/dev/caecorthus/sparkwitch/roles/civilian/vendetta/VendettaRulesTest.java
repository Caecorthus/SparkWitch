package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaRulesTest {
    @Test
    void onlyTheExactUuidBondMayInteractInEitherDirection() {
        UUID vendetta = UUID.randomUUID();
        UUID killer = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertTrue(VendettaRules.isExactPair(vendetta, killer, vendetta, killer, true));
        assertTrue(VendettaRules.isExactPair(killer, vendetta, vendetta, killer, true));
        assertFalse(VendettaRules.isExactPair(vendetta, other, vendetta, killer, true));
        assertFalse(VendettaRules.isExactPair(vendetta, killer, vendetta, killer, false));
    }

    @Test
    void promotionRequiresAStillParticipatingCreditedKiller() {
        assertTrue(VendettaRules.canPromote(true, true, false, false));
        assertFalse(VendettaRules.canPromote(false, true, false, false));
        assertFalse(VendettaRules.canPromote(true, false, false, false));
        assertFalse(VendettaRules.canPromote(true, true, true, false));
        assertFalse(VendettaRules.canPromote(true, true, false, true));
    }

    @Test
    void offlineKillerHasAnExactThirtySecondReconnectGrace() {
        assertEquals(600, VendettaRules.BOUND_KILLER_RECONNECT_GRACE_TICKS);
        assertFalse(VendettaRules.hasReconnectGraceExpired(1_000L, 1_599L));
        assertTrue(VendettaRules.hasReconnectGraceExpired(1_000L, 1_600L));
    }
}
