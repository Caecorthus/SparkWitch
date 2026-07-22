package dev.caecorthus.sparkwitch.client.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurWraithViewerContractTest {
    @Test
    void killerAlliesUseOnlyWatheNativeInstinctPresentation() throws Exception {
        String rules = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/render/WraithViewerRules.java"));
        String invisibility = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithEntityInvisibilityMixin.java"));
        String highlights = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithWatheHighlightMixin.java"));
        assertTrue(rules.contains("shouldRevealPromotedSaboteurToKiller"));
        assertTrue(rules.contains("viewerRole.getFaction() == Faction.KILLER"));
        assertTrue(rules.contains("!shouldRevealPromotedSaboteurToKiller(viewer, target)"));
        assertFalse(invisibility.contains("shouldRevealPromotedSaboteurToKiller"));
        assertFalse(highlights.contains("SaboteurRole.COLOR"));
        assertFalse(highlights.contains(
                "WraithViewerRules.shouldRevealPromotedSaboteurToKiller(viewer, playerTarget)"));
    }
}
