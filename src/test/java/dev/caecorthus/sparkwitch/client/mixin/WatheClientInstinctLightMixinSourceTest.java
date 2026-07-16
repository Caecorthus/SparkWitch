package dev.caecorthus.sparkwitch.client.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatheClientInstinctLightMixinSourceTest {
    private static final Path SOURCE = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WatheClientInstinctLightMixin.java");

    @Test
    void wrapsTheWatheLightGateWithoutReplacingOtherMods() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("@WrapOperation("));
        assertTrue(source.contains("method = \"lambda$onInitializeClient$15\""));
        assertTrue(source.contains(
                "Ldev/doctor4t/wathe/client/WatheClient;isInstinctEnabledAndIsKiller()Z"));
        assertTrue(source.contains("Operation<Boolean> original"));
        assertFalse(source.contains("@Redirect("));
        assertFalse(source.contains("require = 0"));
        assertEquals(1, occurrences(source, "original.call()"));
    }

    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
