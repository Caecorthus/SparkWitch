package dev.caecorthus.sparkwitch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHookResourcesTest {
    private static final Path SPARK_WITCH_CLIENT =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");
    private static final Path INSTINCT_SUPPRESSION_HOOK =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/WitchInstinctSuppressionClientHooks.java");
    private static final Path WATHE_FEAR_INSTINCT_MIXIN =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WatheClientFearInstinctMixin.java");

    @Test
    void clientRegistersWitchInstinctSuppressionHook() throws IOException {
        String source = Files.readString(SPARK_WITCH_CLIENT);

        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.register();"));
    }

    @Test
    void instinctSuppressionHookBeatsHighPriorityRoleHighlights() throws IOException {
        String source = Files.readString(INSTINCT_SUPPRESSION_HOOK);

        assertTrue(source.contains("GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 2"));
        assertTrue(source.contains("GetInstinctHighlight.EVENT.register"));
    }

    @Test
    void watheClientMixinSuppressesEarlyInstinctHighlightReturns() throws IOException {
        String source = Files.readString(WATHE_FEAR_INSTINCT_MIXIN);

        assertTrue(source.contains("priority = 1500"));
        assertTrue(source.contains("method = \"getInstinctHighlight\""));
        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()"));
    }
}
