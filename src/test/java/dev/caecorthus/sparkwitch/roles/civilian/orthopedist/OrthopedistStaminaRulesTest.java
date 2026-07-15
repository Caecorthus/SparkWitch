package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrthopedistStaminaRulesTest {
    @Test
    void reducesOnlyConsumptionByTwentyFivePercent() {
        assertEquals(92.5F, OrthopedistStaminaRules.adjustedValue(100.0F, 90.0F, true, false));
        assertEquals(110.0F, OrthopedistStaminaRules.adjustedValue(100.0F, 110.0F, true, false));
    }

    @Test
    void doesNotApplyWhileHunterInjuryControlsMovement() {
        assertEquals(90.0F, OrthopedistStaminaRules.adjustedValue(100.0F, 90.0F, true, true));
    }

    @Test
    void leavesOrdinaryPlayersUnchanged() {
        assertEquals(90.0F, OrthopedistStaminaRules.adjustedValue(100.0F, 90.0F, false, false));
    }
}
