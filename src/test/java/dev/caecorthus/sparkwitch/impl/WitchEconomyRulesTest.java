package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitchEconomyRulesTest {
    @Test
    void accompliceStartingMoneyUsesWatheKillerFormula() {
        assertEquals(GameConstants.MONEY_START, WitchEconomyRules.killerStartingMoney(24, 4, 6));
        assertEquals(GameConstants.MONEY_START + 15, WitchEconomyRules.killerStartingMoney(25, 4, 6));
        assertEquals(GameConstants.MONEY_START + 60, WitchEconomyRules.killerStartingMoney(28, 4, 6));
    }

    @Test
    void killerStartingMoneyNeverDropsBelowBaseWhenTeamIsOversized() {
        assertEquals(GameConstants.MONEY_START, WitchEconomyRules.killerStartingMoney(20, 4, 6));
        assertEquals(GameConstants.MONEY_START, WitchEconomyRules.killerStartingMoney(20, 4, 0));
    }
}
