package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintGameRecordMixinSourceTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/mixin/GameRecordManagerItemUseMixin.java");

    @Test
    void observesOnlyTheConfirmedSuccessfulItemRecordSeam() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("@Mixin(GameRecordManager.class)"));
        assertTrue(source.contains("method = \"recordItemUse\""));
        assertTrue(source.contains("at = @At(\"HEAD\")"));
        assertTrue(source.contains("SaintKarmaService.onRecordedItemUse"));
        assertFalse(source.contains("cancellable = true"));
        assertFalse(source.contains("require = 0"));
    }
}
