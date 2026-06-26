package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchRoleRegistrationTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void witchFactionRolesResolveToWitchFaction() {
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.grandWitch()));
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.accomplice()));
    }

    @Test
    void apprenticeWitchIsNativeCivilianRole() {
        assertTrue(SparkWitchRoles.apprenticeWitch().isInnocent());
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void murderousWitchIsNativeNeutralRole() {
        assertTrue(SparkWitchRoles.murderousWitch().isNeutral());
        assertEquals(FactionIds.NEUTRAL, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.murderousWitch()));
    }
}
