package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeWraithInstinctWiringSourceTest {
    @Test
    void eligibilityAndFinalVetoBothUseNarrowCreativeRule() throws Exception {
        String wathe = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithWatheHighlightMixin.java"
        ));
        String minecraft = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithMinecraftClientMixin.java"
        ));

        assertTrue(wathe.contains("isInstinctEnabledAndIsKiller()Z"));
        assertTrue(wathe.contains("sparkwitch$allowCreativeWraithInstinct"));
        assertTrue(wathe.contains("CreativeWraithInstinctRules.shouldReveal"));
        assertTrue(minecraft.contains("CreativeWraithInstinctRules.shouldReveal"));
        assertTrue(minecraft.contains("WatheClient.getInstinctHighlight(target) != -1"));
    }
}
