package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithHighlightWiringContractTest {
    @Test
    void watheInstinctResolvesWraithPrivacyAndMinecraftAppliesFinalVeto() throws IOException {
        String wathe = read("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithWatheHighlightMixin.java");
        String outline = read("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithMinecraftClientMixin.java");

        assertTrue(wathe.contains("@Mixin(value = WatheClient.class, remap = false, priority = 2000)"));
        assertTrue(wathe.contains("at = @At(\"HEAD\")"));
        assertTrue(wathe.contains("WraithViewerRules.shouldRevealToSpectator(viewer, playerTarget)"));
        assertTrue(wathe.contains("WatheClient.isInstinctEnabled()"));
        assertTrue(wathe.contains("Objects.requireNonNullElse(game.getRole(playerTarget), WatheRoles.CIVILIAN).color()"));
        assertTrue(wathe.contains("WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, playerTarget)"));
        assertTrue(wathe.contains("cir.setReturnValue(-1)"));
        assertFalse(wathe.contains("sparktraits"));

        assertTrue(outline.contains("@Mixin(value = MinecraftClient.class, priority = 100)"));
        assertTrue(outline.contains("method = \"hasOutline\""));
        assertTrue(outline.contains("WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)"));
        assertTrue(outline.contains("cir.setReturnValue(false)"));
    }

    private static String read(String path) throws IOException {
        Path source = Path.of(path);
        assertTrue(Files.isRegularFile(source), "required source must exist: " + source);
        return Files.readString(source).replaceAll("\\s+", " ");
    }
}
