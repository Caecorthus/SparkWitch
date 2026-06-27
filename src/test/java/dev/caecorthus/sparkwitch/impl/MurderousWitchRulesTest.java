package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MurderousWitchRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void murderousWitchUsesKillerStyleEconomyWithoutTeamShares() {
        assertEquals(Boolean.TRUE, MurderousWitchRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
        assertEquals(Boolean.TRUE, MurderousWitchRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
        assertEquals(Boolean.FALSE, MurderousWitchRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.TEAM_KILL
        ));
        assertNull(MurderousWitchRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.TASK
        ));
        assertNull(MurderousWitchRules.economyDecision(
                WatheRoles.CIVILIAN,
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
    }

    @Test
    void murderousWitchUsesRedInstinctLight() {
        assertEquals(0xC13838, MurderousWitchRules.INSTINCT_COLOR);
        assertTrue(MurderousWitchRules.usesKillerStyleInstinctLight(SparkWitchRoles.murderousWitch()));
        assertFalse(MurderousWitchRules.usesKillerStyleInstinctLight(WatheRoles.KILLER));
    }

    @Test
    void customInstinctHighlightRequiresLivingViewer() {
        assertTrue(MurderousWitchRules.shouldUseCustomInstinctHighlight(true, false));
        assertFalse(MurderousWitchRules.shouldUseCustomInstinctHighlight(false, false));
        assertFalse(MurderousWitchRules.shouldUseCustomInstinctHighlight(true, true));
    }

    @Test
    void ordinaryMurderousWitchInstinctLosesToWatheHardSkips() {
        assertTrue(MurderousWitchRules.INSTINCT_PRIORITY < GetInstinctHighlight.HighlightResult.PRIORITY_HIGH);
    }

    @Test
    void instinctHighlightsOnlyOtherLivingPlayers() {
        assertTrue(MurderousWitchRules.shouldHighlightInstinctTarget(true, false, false, true, false));
        assertFalse(MurderousWitchRules.shouldHighlightInstinctTarget(false, false, false, true, false));
        assertFalse(MurderousWitchRules.shouldHighlightInstinctTarget(true, true, false, true, false));
        assertFalse(MurderousWitchRules.shouldHighlightInstinctTarget(true, false, true, true, false));
        assertFalse(MurderousWitchRules.shouldHighlightInstinctTarget(true, false, false, false, false));
        assertFalse(MurderousWitchRules.shouldHighlightInstinctTarget(true, false, false, true, true));
    }

    @Test
    void winActionRequiresOnlyOneLivingMurderousWitchToRemain() {
        assertEquals(
                MurderousWitchRules.WinAction.NEUTRAL_WIN,
                MurderousWitchRules.winAction(1, 1, GameFunctions.WinStatus.NONE)
        );
        assertEquals(
                MurderousWitchRules.WinAction.BLOCK,
                MurderousWitchRules.winAction(2, 1, GameFunctions.WinStatus.KILLERS)
        );
        assertEquals(
                MurderousWitchRules.WinAction.BLOCK,
                MurderousWitchRules.winAction(2, 2, GameFunctions.WinStatus.PASSENGERS)
        );
        assertEquals(
                MurderousWitchRules.WinAction.NONE,
                MurderousWitchRules.winAction(2, 2, GameFunctions.WinStatus.NONE)
        );
        assertEquals(
                MurderousWitchRules.WinAction.NONE,
                MurderousWitchRules.winAction(1, 0, GameFunctions.WinStatus.KILLERS)
        );
    }
}
