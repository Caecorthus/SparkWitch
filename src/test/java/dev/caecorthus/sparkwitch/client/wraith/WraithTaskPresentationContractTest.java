package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithTaskPresentationContractTest {
    @Test
    void confirmedActiveWraithCanRenderWatheTaskUi() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithMoodRendererMixin.java"
        )).replaceAll("\\s+", " ");

        assertTrue(source.contains("MoodRenderer.class"));
        assertTrue(source.contains("WatheClient;isPlayerPlayingAndAlive()Z"));
        assertTrue(source.contains("WatheClient.isPlayerPlayingAndAlive()"));
        assertTrue(source.contains("WraithClientState.isActive(player)"));
        assertFalse(source.contains("EffectiveTraitService"));
        assertFalse(source.contains("TraitPlayerComponent"));
    }
}
