package dev.caecorthus.sparkwitch.component;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRoundQuotaTest {
    @Test
    void slotConsumptionIsUniqueAndOneWayWithinRound() {
        WraithRoundQuota quota = new WraithRoundQuota();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        quota.beginRound(10);

        assertTrue(quota.tryConsume(first));
        assertFalse(quota.tryConsume(first));
        assertFalse(quota.tryConsume(second));
        assertEquals(1, quota.getConsumedCount());

        quota.clearRound();
        assertEquals(0, quota.getStartingPlayerCount());
        assertEquals(0, quota.getConsumedCount());
    }
}
