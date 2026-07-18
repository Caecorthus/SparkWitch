package dev.caecorthus.sparkwitch.client.witchmaiden;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsInputRulesTest {
    @Test
    void effectForcesPlanarInput() {
        FocusedFootstepsInputRules.PlanarInput input =
                FocusedFootstepsInputRules.forcedPlanarInput();

        assertEquals(1.0F, input.movementForward());
        assertEquals(0.0F, input.movementSideways());
        assertFalse(input.sneaking());
    }

    @Test
    void sprintPhasePreservesExhaustionDowngradeAfterVanillaSelection() {
        assertTrue(FocusedFootstepsInputRules.shouldSprint(0, false, false));
        assertFalse(FocusedFootstepsInputRules.shouldSprint(0, false, true));
        assertTrue(FocusedFootstepsInputRules.shouldSprint(0, true, true));
        assertFalse(FocusedFootstepsInputRules.shouldSprint(1, false, false));
    }
}
