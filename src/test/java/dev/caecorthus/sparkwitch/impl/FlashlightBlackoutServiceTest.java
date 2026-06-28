package dev.caecorthus.sparkwitch.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashlightBlackoutServiceTest {
    @Test
    void litHeldFlashlightCancelsBlackoutBlindnessOnlyForAlivePlayers() {
        assertTrue(FlashlightBlackoutService.shouldCancelBlackoutBlindness(true, true));
        assertFalse(FlashlightBlackoutService.shouldCancelBlackoutBlindness(true, false));
        assertFalse(FlashlightBlackoutService.shouldCancelBlackoutBlindness(false, true));
    }

    @Test
    void blindnessRemovalIsScopedToCurrentBlackoutRefresh() {
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(600, 600));
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(601, 600));
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(598, 600));
        assertFalse(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(590, 600));
        assertFalse(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(620, 600));
    }
}
