package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintHudSourceTest {
    private static final Path RENDERER = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/hud/SaintHudRenderer.java");
    private static final Path MIXIN = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/SaintHudMixin.java");

    @Test
    void stacksHellfireAndKarmaWhenAFormerKillerBecomesSaint() throws IOException {
        String renderer = Files.readString(RENDERER);
        String mixin = Files.readString(MIXIN);

        assertTrue(renderer.contains("List<Text> getHudLines"));
        assertTrue(renderer.contains("lines.add(hellfireLine"));
        assertTrue(renderer.contains("lines.add(Text.translatable(\n                    \"hud.sparkwitch.saint.karma.active\""));
        assertTrue(mixin.contains("List<Text> lines = SaintHudRenderer.getHudLines(player)"));
        assertTrue(mixin.contains("for (Text line : lines)"));
    }
}
