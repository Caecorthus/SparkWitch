package dev.caecorthus.sparkwitch.mana;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchManaRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void manaRolesExcludeAccompliceAndNonWitchRoles() {
        assertTrue(WitchManaRules.isManaRole(SparkWitchRoles.grandWitch()));
        assertTrue(WitchManaRules.isManaRole(SparkWitchRoles.apprenticeWitch()));
        assertTrue(WitchManaRules.isManaRole(SparkWitchRoles.murderousWitch()));

        assertFalse(WitchManaRules.isManaRole(SparkWitchRoles.accomplice()));
        assertFalse(WitchManaRules.isManaRole(WatheRoles.CIVILIAN));
        assertFalse(WitchManaRules.isManaRole(null));
    }

    @Test
    void apprenticeTasksGrantTwentyMana() {
        assertEquals(20, WitchManaRules.taskReward(SparkWitchRoles.apprenticeWitch()));
        assertEquals(0, WitchManaRules.taskReward(SparkWitchRoles.murderousWitch()));
        assertEquals(0, WitchManaRules.taskReward(SparkWitchRoles.grandWitch()));
    }

    @Test
    void naturalRegenerationIntervalMatchesManaRoleTuning() {
        assertEquals(20, WitchManaRules.regenerationIntervalTicks(SparkWitchRoles.grandWitch()));
        assertEquals(40, WitchManaRules.regenerationIntervalTicks(SparkWitchRoles.murderousWitch()));
        assertEquals(60, WitchManaRules.regenerationIntervalTicks(SparkWitchRoles.apprenticeWitch()));
        assertEquals(0, WitchManaRules.regenerationIntervalTicks(SparkWitchRoles.accomplice()));
    }

    @Test
    void naturalRegenerationStopsAtRoleCap() {
        assertEquals(150, WitchManaRules.naturalCap(SparkWitchRoles.murderousWitch()));
        assertEquals(300, WitchManaRules.naturalCap(SparkWitchRoles.grandWitch()));
        assertEquals(100, WitchManaRules.naturalCap(SparkWitchRoles.apprenticeWitch()));

        assertEquals(150, WitchManaRules.applyNaturalRegeneration(149, SparkWitchRoles.murderousWitch()));
        assertEquals(150, WitchManaRules.applyNaturalRegeneration(150, SparkWitchRoles.murderousWitch()));
        assertEquals(151, WitchManaRules.applyNaturalRegeneration(151, SparkWitchRoles.murderousWitch()));
        assertEquals(300, WitchManaRules.applyNaturalRegeneration(299, SparkWitchRoles.grandWitch()));
        assertEquals(301, WitchManaRules.applyNaturalRegeneration(301, SparkWitchRoles.grandWitch()));
        assertEquals(100, WitchManaRules.applyNaturalRegeneration(99, SparkWitchRoles.apprenticeWitch()));
        assertEquals(100, WitchManaRules.applyNaturalRegeneration(100, SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void killRewardsPreferWitchKillTotalOverGenericKillReward() {
        assertEquals(50, WitchManaRules.killReward(
                SparkWitchRoles.murderousWitch(),
                SparkWitchRoles.apprenticeWitch()
        ));
        assertEquals(100, WitchManaRules.killReward(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.murderousWitch()
        ));
        assertEquals(50, WitchManaRules.killReward(
                SparkWitchRoles.apprenticeWitch(),
                SparkWitchRoles.grandWitch()
        ));
    }

    @Test
    void onlyMurderousAndGrandWitchesGetGenericKillRewards() {
        assertEquals(25, WitchManaRules.killReward(
                SparkWitchRoles.murderousWitch(),
                WatheRoles.CIVILIAN
        ));
        assertEquals(50, WitchManaRules.killReward(
                SparkWitchRoles.grandWitch(),
                WatheRoles.CIVILIAN
        ));
        assertEquals(0, WitchManaRules.killReward(
                SparkWitchRoles.apprenticeWitch(),
                WatheRoles.CIVILIAN
        ));
        assertEquals(0, WitchManaRules.killReward(
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.apprenticeWitch()
        ));
    }

    @Test
    void accompliceKillsCreateGrandWitchManaRewardsWithoutGivingAccompliceMana() {
        assertEquals(50, WitchManaRules.grandWitchRewardForAccompliceKill(
                SparkWitchRoles.accomplice(),
                WatheRoles.CIVILIAN
        ));
        assertEquals(100, WitchManaRules.grandWitchRewardForAccompliceKill(
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.apprenticeWitch()
        ));
        assertEquals(0, WitchManaRules.grandWitchRewardForAccompliceKill(
                SparkWitchRoles.grandWitch(),
                WatheRoles.CIVILIAN
        ));
    }
}
