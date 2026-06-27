package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FirePokerFallAttributionServiceTest {
    private final UUID firstPusher = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID secondPusher = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID target = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void clearAttributions() {
        FirePokerFallAttributionService.clearAll();
    }

    @Test
    void activeFallOutOfTrainConsumesLastFirePokerPusher() {
        FirePokerFallAttributionService.recordPush(firstPusher, target, 100);
        FirePokerFallAttributionService.recordPush(secondPusher, target, 120);

        assertEquals(
                secondPusher,
                FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 319)
        );
        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 320));
    }

    @Test
    void expiredAttributionFallsBackAndClearsRecord() {
        FirePokerFallAttributionService.recordPush(firstPusher, target, 100);

        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 301));
        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 101));
    }

    @Test
    void nonTrainFallDeathDoesNotConsumeAttribution() {
        FirePokerFallAttributionService.recordPush(firstPusher, target, 100);

        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.DROWNED, 120));
        assertEquals(
                firstPusher,
                FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 120)
        );
    }

    @Test
    void selfPushIsIgnored() {
        FirePokerFallAttributionService.recordPush(target, target, 100);

        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 120));
    }

    @Test
    void clearingPlayerRemovesTargetAndPusherRecords() {
        UUID otherTarget = UUID.fromString("00000000-0000-0000-0000-000000000004");
        FirePokerFallAttributionService.recordPush(firstPusher, target, 100);
        FirePokerFallAttributionService.recordPush(target, otherTarget, 100);

        FirePokerFallAttributionService.clearPlayer(target);

        assertNull(FirePokerFallAttributionService.consumePusherUuid(target, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 120));
        assertNull(FirePokerFallAttributionService.consumePusherUuid(otherTarget, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, 120));
    }
}
