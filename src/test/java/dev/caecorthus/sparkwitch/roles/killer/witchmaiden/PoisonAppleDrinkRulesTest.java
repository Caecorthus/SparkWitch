package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PoisonAppleDrinkRulesTest {
    @Test
    void appliesOnlyToMarkedCocktails() {
        assertTrue(PoisonAppleDrinkRules.shouldApply(true, true));
        assertFalse(PoisonAppleDrinkRules.shouldApply(true, false));
        assertFalse(PoisonAppleDrinkRules.shouldApply(false, true));
        assertFalse(PoisonAppleDrinkRules.shouldApply(false, false));
    }
}
