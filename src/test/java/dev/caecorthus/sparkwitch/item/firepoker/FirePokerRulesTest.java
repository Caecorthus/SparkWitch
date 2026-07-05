package dev.caecorthus.sparkwitch.item.firepoker;

import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirePokerRulesTest {
    @Test
    void firePokerUsesPlannedCooldownManaAndEffects() {
        assertEquals(GameConstants.getInTicks(0, 10), FirePokerRules.COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(0, 10), FirePokerRules.FALL_ATTRIBUTION_WINDOW_TICKS);
        assertEquals(20, FirePokerRules.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 3), FirePokerRules.EFFECT_DURATION_TICKS);
        assertEquals(2, FirePokerRules.SLOWNESS_AMPLIFIER);
        assertEquals(2, FirePokerRules.SPEED_AMPLIFIER);
        assertEquals(10.0, FirePokerRules.KNOCKBACK_STRENGTH);
    }

    @Test
    void magicEffectsRequireManaSystemAndEnoughMana() {
        assertTrue(FirePokerRules.shouldSpendMana(true, 20));
        assertTrue(FirePokerRules.shouldSpendMana(true, 21));
        assertFalse(FirePokerRules.shouldSpendMana(true, 19));
        assertFalse(FirePokerRules.shouldSpendMana(false, 200));
    }
}
