package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PoisonAppleAntidoteRulesTest {
    @Test
    void onlyReadyToxicologistAntidoteCanClearPoisonAppleState() {
        assertTrue(PoisonAppleAntidoteRules.canCure(true, true, false));
        assertFalse(PoisonAppleAntidoteRules.canCure(false, true, false));
        assertFalse(PoisonAppleAntidoteRules.canCure(true, false, false));
        assertFalse(PoisonAppleAntidoteRules.canCure(true, true, true));
    }
}
