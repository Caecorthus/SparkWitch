package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRulesTest {
    @Test
    void quotaStartsAtTenAndGrowsEveryFivePlayers() {
        assertEquals(0, WraithRules.randomCap(9));
        assertEquals(1, WraithRules.randomCap(10));
        assertEquals(1, WraithRules.randomCap(14));
        assertEquals(2, WraithRules.randomCap(15));
    }

    @Test
    void eligibleFactionsIncludeCapturedWitchAlignment() {
        assertTrue(WraithRules.isEligibleFaction(FactionIds.CIVILIAN));
        assertTrue(WraithRules.isEligibleFaction(FactionIds.KILLER));
        assertTrue(WraithRules.isEligibleFaction(SparkWitchFactions.WITCH));
        assertFalse(WraithRules.isEligibleFaction(FactionIds.NEUTRAL));
        assertEquals(WraithState.Alignment.CIVILIAN, WraithRules.effectiveAlignment(FactionIds.CIVILIAN));
        assertEquals(WraithState.Alignment.KILLER, WraithRules.effectiveAlignment(FactionIds.KILLER));
        assertEquals(WraithState.Alignment.WITCH, WraithRules.effectiveAlignment(SparkWitchFactions.WITCH));
        assertThrows(IllegalArgumentException.class, () -> WraithRules.effectiveAlignment(FactionIds.NEUTRAL));
    }

    @Test
    void onlyPlayerAttributedFallsRemainEligible() {
        assertFalse(WraithRules.isEligibleDeath(
                FactionIds.CIVILIAN,
                GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
                false
        ));
        assertTrue(WraithRules.isEligibleDeath(
                FactionIds.CIVILIAN,
                GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
                true
        ));
    }

    @Test
    void escapedAndDigestedDeathsAreTerminal() {
        assertFalse(WraithRules.isEligibleDeath(
                FactionIds.CIVILIAN,
                GameConstants.DeathReasons.ESCAPED,
                false
        ));
        assertFalse(WraithRules.isEligibleDeath(
                FactionIds.CIVILIAN,
                Identifier.of("noellesroles", "digested"),
                false
        ));
    }

    @Test
    void conversionRollUsesStrictSeventyFivePercentBoundary() {
        assertTrue(WraithRules.shouldBecomeWraith(
                FactionIds.KILLER,
                GameConstants.DeathReasons.KNIFE,
                false,
                0.749999D
        ));
        assertFalse(WraithRules.shouldBecomeWraith(
                FactionIds.KILLER,
                GameConstants.DeathReasons.KNIFE,
                false,
                0.75D
        ));
    }

    @Test
    void fallbackAtTheTrainFloorIsAcceptedButAnythingBelowIsRejected() {
        assertTrue(WraithRules.fallbackIsAboveTrain(118.0D, 118.0D));
        assertFalse(WraithRules.fallbackIsAboveTrain(117.999D, 118.0D));
    }
}
