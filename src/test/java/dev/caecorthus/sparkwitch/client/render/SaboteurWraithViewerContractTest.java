package dev.caecorthus.sparkwitch.client.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurWraithViewerContractTest {
    @Test
    void livingKillerInstinctUsesSaboteurRoleColorWithoutChangingBaseWraithInstinct() throws Exception {
        String rules = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/render/WraithViewerRules.java"));
        String invisibility = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithEntityInvisibilityMixin.java"));
        String highlights = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithWatheHighlightMixin.java"));
        assertTrue(rules.contains("shouldRevealPromotedSaboteurToKiller"));
        assertTrue(rules.contains("GameFunctions.isPlayerPlayingAndAlive(viewer)"));
        assertTrue(rules.contains("viewerRole.getFaction() == Faction.KILLER"));
        assertTrue(rules.contains("!shouldRevealPromotedSaboteurToKiller(viewer, target)"));
        assertFalse(invisibility.contains("shouldRevealPromotedSaboteurToKiller"));
        assertTrue(highlights.contains("SaboteurRules.instinctHighlight("));
        assertTrue(highlights.contains("WatheClient.isInstinctEnabled()"));
        assertTrue(highlights.contains("WraithViewerRules.shouldRevealPromotedSaboteurToKiller(viewer, playerTarget)"));
        assertTrue(highlights.contains("SaboteurRules.isActivePromotedSaboteur(playerTarget)"));
    }
}
