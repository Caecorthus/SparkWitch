package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VendettaKnifeRulesTest {
    @Test
    void acceptsOnlyCompleteBoundKillerValidationAtExactBoundaries() {
        assertTrue(VendettaKnifeRules.canAttempt(
                true, true, true, true, true, 10, 9.0D, true));
        assertFalse(VendettaKnifeRules.canAttempt(
                true, true, true, true, true, 9, 9.0D, true));
        assertFalse(VendettaKnifeRules.canAttempt(
                true, true, true, true, true, 10, Math.nextUp(9.0D), true));
        assertFalse(VendettaKnifeRules.canAttempt(
                true, true, true, false, true, 10, 1.0D, true));
        assertFalse(VendettaKnifeRules.canAttempt(
                true, true, true, true, true, 10, 1.0D, false));
    }

    @Test
    void consumesOnlyOnAConfirmedAliveToDeadTransition() {
        assertTrue(VendettaKnifeRules.confirmedDeath(false, true));
        assertFalse(VendettaKnifeRules.confirmedDeath(false, false));
        assertFalse(VendettaKnifeRules.confirmedDeath(true, true));
    }
}
