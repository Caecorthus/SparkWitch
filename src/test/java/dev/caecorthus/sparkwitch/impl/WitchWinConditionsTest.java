package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameFunctions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchWinConditionsTest {
    @Test
    void witchFactionWinsWhenOnlyLivingPlayersAreWitches() {
        assertEquals(
                WitchWinConditions.WinAction.FACTION_WIN,
                WitchWinConditions.winAction(1, 1, GameFunctions.WinStatus.NONE)
        );
        assertEquals(
                WitchWinConditions.WinAction.FACTION_WIN,
                WitchWinConditions.winAction(2, 2, GameFunctions.WinStatus.PASSENGERS)
        );
    }

    @Test
    void witchAndAlliedShadowJestersTriggerShowdownInsteadOfWitchWin() {
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.TRIGGER_AND_BLOCK,
                WitchWinConditions.shadowShowdownAction(1, 0, 2, 0, false)
        );
    }

    @Test
    void witchKillerAndAlliedShadowJestersDoNotLetWitchSettleFirst() {
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.TRIGGER_AND_BLOCK,
                WitchWinConditions.shadowShowdownAction(1, 1, 2, 0, false)
        );
    }

    @Test
    void activeShadowShowdownKeepsBlockingWitchSettlement() {
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.BLOCK,
                WitchWinConditions.shadowShowdownAction(1, 1, 2, 0, true)
        );
    }

    @Test
    void activeShadowShowdownNeutralWinIsBlockedByLivingWitches() {
        assertTrue(WitchWinConditions.shouldBlockShadowJesterShowdownNeutralWin(1));
        assertFalse(WitchWinConditions.shouldBlockShadowJesterShowdownNeutralWin(0));
    }

    @Test
    void shadowShowdownBridgeIgnoresUnrelatedLivingPlayers() {
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.NONE,
                WitchWinConditions.shadowShowdownAction(1, 0, 2, 1, false)
        );
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.NONE,
                WitchWinConditions.shadowShowdownAction(1, 1, 0, 0, false)
        );
    }

    @Test
    void lastStandTriggeredOtherPlayersDoNotBlockShadowShowdown() {
        int lastStandTriggeredOtherCount = WitchWinConditions.countsAsShadowShowdownOther(true) ? 1 : 0;
        int ordinaryOtherCount = WitchWinConditions.countsAsShadowShowdownOther(false) ? 1 : 0;

        assertEquals(
                WitchWinConditions.ShadowShowdownAction.TRIGGER_AND_BLOCK,
                WitchWinConditions.shadowShowdownAction(1, 0, 2, lastStandTriggeredOtherCount, false)
        );
        assertEquals(
                WitchWinConditions.ShadowShowdownAction.NONE,
                WitchWinConditions.shadowShowdownAction(1, 0, 2, ordinaryOtherCount, false)
        );
    }

    @Test
    void livingWitchesBlockNativePassengerAndKillerWins() {
        assertEquals(
                WitchWinConditions.WinAction.BLOCK,
                WitchWinConditions.winAction(3, 1, GameFunctions.WinStatus.PASSENGERS)
        );
        assertEquals(
                WitchWinConditions.WinAction.BLOCK,
                WitchWinConditions.winAction(3, 1, GameFunctions.WinStatus.KILLERS)
        );
    }

    @Test
    void noWitchesOrNoNativeTeamWinDoesNotChangeRoundEnd() {
        assertEquals(
                WitchWinConditions.WinAction.NONE,
                WitchWinConditions.winAction(3, 0, GameFunctions.WinStatus.PASSENGERS)
        );
        assertEquals(
                WitchWinConditions.WinAction.NONE,
                WitchWinConditions.winAction(3, 1, GameFunctions.WinStatus.NONE)
        );
        assertEquals(
                WitchWinConditions.WinAction.NONE,
                WitchWinConditions.winAction(3, 1, GameFunctions.WinStatus.TIME)
        );
    }
}
