package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRulesTest {
    @Test
    void quotaUsesTheFixedStartingRoster() {
        assertEquals(0, WraithRules.randomCap(9));
        assertEquals(1, WraithRules.randomCap(10));
        assertEquals(1, WraithRules.randomCap(14));
        assertEquals(2, WraithRules.randomCap(15));
        assertEquals(3, WraithRules.randomCap(20));
    }

    @Test
    void conversionUsesTheExactChanceAndEligibleNativeOrigins() {
        assertFalse(WraithRules.passesChance(0.75D));
        assertTrue(WraithRules.passesChance(Math.nextDown(0.75D)));
        assertTrue(WraithRules.isEligibleDeath(Faction.CIVILIAN, GameConstants.DeathReasons.GENERIC));
        assertTrue(WraithRules.isEligibleDeath(Faction.KILLER, GameConstants.DeathReasons.GENERIC));
        assertFalse(WraithRules.isEligibleDeath(Faction.NEUTRAL, GameConstants.DeathReasons.GENERIC));
        assertFalse(WraithRules.isEligibleDeath(Faction.CIVILIAN, GameConstants.DeathReasons.ESCAPED));
        assertFalse(WraithRules.isEligibleDeath(Faction.KILLER, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN));
    }

    @Test
    void effectiveFactionSnapshotMapsOnlyGoodAndKiller() {
        assertEquals(WraithState.Alignment.GOOD, WraithRules.alignmentFor(FactionIds.CIVILIAN));
        assertEquals(WraithState.Alignment.KILLER, WraithRules.alignmentFor(FactionIds.KILLER));
        assertThrows(IllegalArgumentException.class, () -> WraithRules.alignmentFor(FactionIds.NEUTRAL));
    }
}
