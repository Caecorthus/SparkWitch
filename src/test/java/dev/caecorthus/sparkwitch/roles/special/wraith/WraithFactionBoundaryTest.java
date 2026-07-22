package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithFactionBoundaryTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void savedAlignmentOverridesOnlyRestrictedWraith() throws Exception {
        String participation = source("runtime/WraithParticipation.java");

        assertTrue(participation.contains("if (!restricted || alignment == null)"));
        assertTrue(participation.contains("case GOOD -> FactionIds.CIVILIAN"));
        assertTrue(participation.contains("case KILLER -> FactionIds.KILLER"));
        assertTrue(participation.contains("case WITCH -> SparkWitchFactions.WITCH"));
        assertTrue(participation.contains("restrictedFaction(wraith.isRestricted(), wraith.getAlignment())"));
        assertEquals(SparkWitchFactions.WITCH,
                SparkFactionApi.resolveBaseFaction(SparkWitchRoles.curser()));
    }

    @Test
    void promotionNeverResurrectsCurserOrRemovesWatheDeadMembership() throws Exception {
        String promotion = source("progression/WraithPromotionQueue.java");
        String promotionService = source("progression/WraithPromotionService.java");
        String lifecycle = source("runtime/WraithLifecycle.java");
        assertTrue(promotion.contains("WraithPromotionService.promote(player"));
        assertTrue(promotionService.contains("wraith.promote()"));
        assertTrue(promotionService.contains("WraithLifecycle.promotePlayer(player, role)"));
        assertTrue(lifecycle.contains("transitionRole(player, role)"));
        assertFalse(promotion.contains("removeDeadPlayer"));
        assertFalse(promotion.contains("setPlayerAlive"));
        assertFalse(promotionService.contains("removeDeadPlayer"));
        assertFalse(promotionService.contains("setPlayerAlive"));
        assertFalse(lifecycle.contains("removeDeadPlayer"));
        assertFalse(lifecycle.contains("setPlayerAlive"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith", relative));
    }
}
