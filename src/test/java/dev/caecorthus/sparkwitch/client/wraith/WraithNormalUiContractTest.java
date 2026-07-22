package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithNormalUiContractTest {
    private static final Path CLIENT_MIXINS = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin"
    );

    @Test
    void activeWraithKeepsNativeTextChatAndNormalInventoryOpening() throws IOException {
        String config = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));
        String sources;
        try (Stream<Path> paths = Files.walk(Path.of("src/client/java"))) {
            StringBuilder joined = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                joined.append(Files.readString(path)).append('\n');
            }
            sources = joined.toString();
        }

        assertFalse(
                Files.exists(CLIENT_MIXINS.resolve("WraithChatRestrictionMixin.java"))
        );
        assertFalse(Files.exists(CLIENT_MIXINS.resolve("WraithChatScreenMixin.java")));
        assertFalse(Files.exists(CLIENT_MIXINS.resolve("WraithInventoryKeyMixin.java")));
        assertFalse(config.contains("WraithChatRestrictionMixin"));
        assertFalse(config.contains("WraithChatScreenMixin"));
        assertFalse(config.contains("WraithInventoryKeyMixin"));
        assertFalse(sources.contains("setChatAllowed(false)"));
        assertFalse(sources.contains("shouldDisableChat()Z"));
        assertFalse(sources.contains("sparkwitch$openWraithInventory"));
    }

    @Test
    void wraithNeverEntersTheWitchSkillPanel() throws IOException {
        String inventory = Files.readString(CLIENT_MIXINS.resolve("WitchSkillInventoryScreenMixin.java"));
        assertFalse(inventory.contains("Wraith"));
        assertFalse(inventory.contains("curser"));
        assertFalse(inventory.contains("saboteur"));
        assertFalse(inventory.contains("wind_spirit"));
        assertFalse(inventory.contains("guardian_angel"));
        assertFalse(inventory.contains("vendetta"));
    }
}
