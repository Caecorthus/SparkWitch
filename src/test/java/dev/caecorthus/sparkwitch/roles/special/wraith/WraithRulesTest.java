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
    void conversionUsesTheExactChanceAndEligibleNativeOrigins() {
        assertFalse(WraithRules.passesChance(0.75D));
        assertTrue(WraithRules.passesChance(Math.nextDown(0.75D)));
        assertFalse(WraithRules.passesChance(0.0D, 0));
        assertTrue(WraithRules.passesChance(Math.nextDown(1.0D), 100));
        assertFalse(WraithRules.passesChance(1.0D, 100));
        assertTrue(WraithRules.isEligibleDeath(Faction.CIVILIAN, GameConstants.DeathReasons.GENERIC));
        assertTrue(WraithRules.isEligibleDeath(Faction.KILLER, GameConstants.DeathReasons.GENERIC));
        assertFalse(WraithRules.isEligibleDeath(Faction.NEUTRAL, GameConstants.DeathReasons.GENERIC));
        assertFalse(WraithRules.isEligibleDeath(Faction.CIVILIAN, GameConstants.DeathReasons.ESCAPED));
        assertFalse(WraithRules.isEligibleDeath(Faction.KILLER, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN));
        assertTrue(WraithRules.isEligibleDeath(
                Faction.KILLER, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, true));
    }

    @Test
    void effectiveFactionSnapshotMapsGoodKillerAndWitch() {
        assertEquals(WraithState.Alignment.GOOD, WraithRules.alignmentFor(FactionIds.CIVILIAN));
        assertEquals(WraithState.Alignment.KILLER, WraithRules.alignmentFor(FactionIds.KILLER));
        assertEquals(WraithState.Alignment.WITCH,
                WraithRules.alignmentFor(net.minecraft.util.Identifier.of("sparkwitch", "witch")));
        assertTrue(WraithRules.isEligibleDeath(
                WraithState.Alignment.WITCH, GameConstants.DeathReasons.GENERIC, false));
        assertFalse(WraithRules.isEligibleDeath(
                WraithState.Alignment.WITCH, GameConstants.DeathReasons.ESCAPED, false));
        assertThrows(IllegalArgumentException.class, () -> WraithRules.alignmentFor(FactionIds.NEUTRAL));
    }
}
