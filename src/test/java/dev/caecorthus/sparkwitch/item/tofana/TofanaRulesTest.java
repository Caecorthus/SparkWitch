package dev.caecorthus.sparkwitch.item.tofana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TofanaRulesTest {
    @Test
    void freezesRetaliationDeathReason() {
        assertEquals("sparkwitch:tofana_elixir", TofanaRules.DEATH_REASON_ID.toString());
    }

    @Test
    void protectsAnyHolderFromAValidNonForcedPlayerKill() {
        assertTrue(TofanaRules.canProtect(false, true, true));
        assertFalse(TofanaRules.canProtect(true, true, true));
        assertFalse(TofanaRules.canProtect(false, false, true));
        assertFalse(TofanaRules.canProtect(false, true, false));
    }
}
