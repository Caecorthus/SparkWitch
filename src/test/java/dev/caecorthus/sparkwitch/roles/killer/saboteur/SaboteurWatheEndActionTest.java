package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurWatheEndActionTest {
    @Test
    void watheOnlyNativeEndKeepsUpstreamDuplicateBehavior() {
        assertFalse(SaboteurLightOutageService.WatheEndAction.NATIVE.coordinatesLampRestoration());
    }

    @Test
    void combinedLeaseEndsSuppressTheSecondNativeWrite() {
        assertTrue(SaboteurLightOutageService.WatheEndAction.KEEP_DARK.coordinatesLampRestoration());
        assertTrue(SaboteurLightOutageService.WatheEndAction.RESTORE_AFTER_NATIVE.coordinatesLampRestoration());
    }
}
