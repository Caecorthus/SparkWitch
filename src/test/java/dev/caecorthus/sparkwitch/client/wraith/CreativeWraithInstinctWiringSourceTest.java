package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CreativeWraithInstinctWiringSourceTest {
    @Test
    void creativeModeDoesNotBypassWraithInstinctPrivacy() throws Exception {
        String wathe = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithWatheHighlightMixin.java"
        ));
        String minecraft = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithMinecraftClientMixin.java"
        ));

        assertFalse(wathe.contains("sparkwitch$allowCreativeWraithInstinct"));
        assertFalse(wathe.contains("CreativeWraithInstinctRules.shouldReveal"));
        assertFalse(minecraft.contains("CreativeWraithInstinctRules.shouldReveal"));
    }
}
