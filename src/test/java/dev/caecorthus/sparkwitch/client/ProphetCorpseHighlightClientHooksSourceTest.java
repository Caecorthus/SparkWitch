package dev.caecorthus.sparkwitch.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetCorpseHighlightClientHooksSourceTest {
    private static final Path HOOK = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/hooks/ProphetCorpseHighlightClientHooks.java");
    private static final Path CLIENT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");

    @Test
    void usesThePublicAlwaysOnBodyHighlightEvent() throws IOException {
        assertTrue(Files.exists(HOOK), "Prophet corpse highlight hook must exist");
        String hook = Files.readString(HOOK);

        assertTrue(hook.contains("GetInstinctHighlight.EVENT.register"));
        assertTrue(hook.contains("target instanceof PlayerBodyEntity body"));
        assertTrue(hook.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(hook.contains("GameFunctions.isPlayerPlayingAndAlive(viewer)"));
        assertTrue(hook.contains("GameFunctions.isPlayerSpectatingOrCreative(viewer)"));
        assertTrue(hook.contains("ProphetRules.isProphet"));
        assertTrue(hook.contains("component.isDeathOmenActive()"));
        assertTrue(hook.contains("component.isDeathOmenBody(body.getUuid())"));
        assertTrue(hook.contains("NoellesHiddenBodiesBridge.isHidden"));
        assertFalse(hook.contains("HiddenBodiesWorldComponent"));
        assertTrue(hook.contains("HighlightResult.always"));
        assertTrue(hook.contains("ProphetRules.CORPSE_HIGHLIGHT_COLOR"));
        assertTrue(hook.contains("ProphetRules.CORPSE_HIGHLIGHT_PRIORITY"));
        assertFalse(hook.contains("squaredDistanceTo"));
        assertFalse(hook.contains("canSee("));
    }

    @Test
    void registrationIsIdempotentAndWiredOnce() throws IOException {
        assertTrue(Files.exists(HOOK), "Prophet corpse highlight hook must exist");
        String hook = Files.readString(HOOK);
        String client = Files.readString(CLIENT);

        assertTrue(hook.contains("private static boolean registered;"));
        assertTrue(hook.contains("public static synchronized void register()"));
        assertTrue(hook.contains("if (registered)"));
        assertEquals(1, occurrences(client, "ProphetCorpseHighlightClientHooks.register()"));
    }

    private static int occurrences(String source, String expected) {
        return source.split(java.util.regex.Pattern.quote(expected), -1).length - 1;
    }
}
