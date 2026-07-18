package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaPresentationRulesTest {
    @Test
    void pairHighlightsUseLiteralRedAndOrange() {
        assertEquals(0xFF0000, VendettaPresentationRules.KILLER_HIGHLIGHT_COLOR);
        assertEquals(0xFF8C00, VendettaPresentationRules.VENDETTA_HIGHLIGHT_COLOR);
    }

    @Test
    void desaturationRunsFromHalfAtFifteenBlocksToFullAtOneBlock() {
        assertEquals(0.50F, VendettaPresentationRules.desaturation(Double.POSITIVE_INFINITY));
        assertEquals(0.50F, VendettaPresentationRules.desaturation(15.0D));
        assertEquals(0.75F, VendettaPresentationRules.desaturation(8.0D));
        assertEquals(1.0F, VendettaPresentationRules.desaturation(1.0D));
        assertEquals(1.0F, VendettaPresentationRules.desaturation(0.0D));
    }

    @Test
    void proximityOrRevealWindowCanHighlightTheBoundKiller() {
        assertTrue(VendettaPresentationRules.shouldHighlightKiller(4.0D, 0));
        assertFalse(VendettaPresentationRules.shouldHighlightKiller(4.01D, 0));
        assertTrue(VendettaPresentationRules.shouldHighlightKiller(100.0D, 1));
    }

    @Test
    void onlyOwnerAndLivingBoundKillerReceiveKnifeEquipment() {
        assertTrue(VendettaPresentationRules.canSeeKnifeEquipment(true, false, false));
        assertTrue(VendettaPresentationRules.canSeeKnifeEquipment(false, true, false));
        assertFalse(VendettaPresentationRules.canSeeKnifeEquipment(false, false, false));
        assertFalse(VendettaPresentationRules.canSeeKnifeEquipment(false, true, true));
    }

    @Test
    void hudRoundsPartialSecondsUpWithoutInventingTimeAtZero() {
        assertEquals(0, VendettaPresentationRules.secondsRemaining(0));
        assertEquals(1, VendettaPresentationRules.secondsRemaining(1));
        assertEquals(1, VendettaPresentationRules.secondsRemaining(20));
        assertEquals(2, VendettaPresentationRules.secondsRemaining(21));
    }
}
