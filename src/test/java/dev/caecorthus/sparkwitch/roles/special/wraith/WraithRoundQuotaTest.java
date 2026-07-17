package dev.caecorthus.sparkwitch.component;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRoundQuotaTest {
    @Test
    void fixesTheCapFromTheStartingRoster() {
        assertEquals(0, WraithRoundQuota.capForStartingPlayers(9));
        assertEquals(1, WraithRoundQuota.capForStartingPlayers(10));
        assertEquals(1, WraithRoundQuota.capForStartingPlayers(14));
        assertEquals(2, WraithRoundQuota.capForStartingPlayers(15));
    }

    @Test
    void consumedPlayersPermanentlyUseTheRoundQuota() {
        WraithRoundQuota quota = new WraithRoundQuota();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        quota.beginRound(10);

        assertEquals(1, quota.getCap());
        assertTrue(quota.tryConsume(first));
        assertFalse(quota.tryConsume(first));
        assertFalse(quota.tryConsume(second));
        assertEquals(1, quota.getConsumedCount());
    }

    @Test
    void restoresStartingCountAndConsumedSlots() {
        WraithRoundQuota quota = new WraithRoundQuota();
        UUID first = UUID.randomUUID();

        quota.restore(15, Set.of(first));

        assertEquals(15, quota.getStartingPlayerCount());
        assertEquals(2, quota.getCap());
        assertEquals(1, quota.getConsumedCount());
        assertFalse(quota.tryConsume(first));
        assertTrue(quota.tryConsume(UUID.randomUUID()));
    }
}
