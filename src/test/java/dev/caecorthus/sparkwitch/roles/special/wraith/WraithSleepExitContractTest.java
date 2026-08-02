package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithSleepExitContractTest {
    private static final Path MAIN_MIXIN_ROOT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/mixin"
    );
    private static final Path CLIENT_MIXIN_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin"
    );

    @Test
    void registeredWraithRestrictionsLeaveVanillaSleepingExitAvailable() throws Exception {
        String participation = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/runtime/"
                        + "WraithParticipation.java"
        ));
        String blockUse = between(
                participation,
                "UseBlockCallback.EVENT.register",
                "UseEntityCallback.EVENT.register"
        );
        String jumpKey = Files.readString(CLIENT_MIXIN_ROOT.resolve("WraithJumpKeyBindingMixin.java"));
        String wraithMixins = registeredWraithMixins(
                Path.of("src/main/resources/sparkwitch.mixins.json"),
                "mixins",
                MAIN_MIXIN_ROOT
        ) + registeredWraithMixins(
                Path.of("src/client/resources/sparkwitch.client.mixins.json"),
                "client",
                CLIENT_MIXIN_ROOT
        );

        assertTrue(blockUse.contains("block instanceof BedBlock"));
        assertTrue(blockUse.contains("? ActionResult.PASS : ActionResult.FAIL"));
        assertTrue(jumpKey.contains("(Object) this != options.jumpKey"));
        assertFalse(wraithMixins.contains("SleepingChatScreen"));
        assertFalse(wraithMixins.contains("ClientCommandC2SPacket"));
        assertFalse(wraithMixins.contains("STOP_SLEEPING"));
    }

    private static String registeredWraithMixins(Path configPath, String arrayName, Path root)
            throws IOException {
        JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        StringBuilder sources = new StringBuilder();
        for (var value : config.getAsJsonArray(arrayName)) {
            String name = value.getAsString();
            if (!name.startsWith("Wraith")) {
                continue;
            }
            Path source = root.resolve(name.replace('.', '/') + ".java");
            assertTrue(Files.isRegularFile(source), name);
            sources.append(Files.readString(source));
        }
        return sources.toString();
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);
        return source.substring(startIndex, endIndex);
    }
}
