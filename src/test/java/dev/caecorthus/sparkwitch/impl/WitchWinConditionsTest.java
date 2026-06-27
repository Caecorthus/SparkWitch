package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameFunctions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
