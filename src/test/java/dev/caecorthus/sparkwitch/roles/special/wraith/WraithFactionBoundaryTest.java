package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithFactionBoundaryTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void savedAlignmentOverridesOnlyRestrictedWraith() {
        assertEquals(FactionIds.CIVILIAN,
                WraithFactionService.restrictedFaction(true, WraithState.Alignment.GOOD));
        assertEquals(FactionIds.KILLER,
                WraithFactionService.restrictedFaction(true, WraithState.Alignment.KILLER));
        assertNull(WraithFactionService.restrictedFaction(false, WraithState.Alignment.KILLER));
        assertEquals(SparkWitchFactions.WITCH,
                SparkFactionApi.resolveBaseFaction(SparkWitchRoles.curser()));
    }

    @Test
    void promotionNeverResurrectsCurserOrRemovesWatheDeadMembership() throws Exception {
        String promotion = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithPromotionService.java"));
        assertTrue(promotion.contains("wraith.promote()"));
        assertTrue(promotion.contains("WraithRoleTransitionService.transition(player, role)"));
        assertTrue(!promotion.contains("removeDeadPlayer") && !promotion.contains("setPlayerAlive"));
    }
}
