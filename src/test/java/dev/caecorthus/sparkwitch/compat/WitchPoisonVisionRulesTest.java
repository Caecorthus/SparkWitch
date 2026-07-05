package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchPoisonVisionRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void murderousWitchAccompliceAndGrandWitchCanSeeHiddenPoison() {
        assertTrue(WitchPoisonVisionRules.canSeeHiddenPoison(SparkWitchRoles.murderousWitch()));
        assertTrue(WitchPoisonVisionRules.canSeeHiddenPoison(SparkWitchRoles.accomplice()));
        assertTrue(WitchPoisonVisionRules.canSeeHiddenPoison(SparkWitchRoles.grandWitch()));
    }

    @Test
    void apprenticeAndNativeRolesDoNotGainSparkWitchPoisonVision() {
        assertFalse(WitchPoisonVisionRules.canSeeHiddenPoison(SparkWitchRoles.apprenticeWitch()));
        assertFalse(WitchPoisonVisionRules.canSeeHiddenPoison(WatheRoles.CIVILIAN));
        assertFalse(WitchPoisonVisionRules.canSeeHiddenPoison(WatheRoles.KILLER));
    }
}
