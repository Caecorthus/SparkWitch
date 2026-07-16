package dev.caecorthus.sparkwitch.roles.killer.ninja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaTraitCompatibilityResourcesTest {
    private static final Path TAG_ROOT = Path.of("src/main/resources/data/sparktraits/tags/item");

    @Test
    void contributesKunaiWithoutReplacingExistingFeatherBladeSupport() throws IOException {
        assertKunaiContributor("bloodthirsty_weapons.json");
        assertKunaiContributor("thrust_weapons.json");
    }

    private static void assertKunaiContributor(String fileName) throws IOException {
        Path path = TAG_ROOT.resolve(fileName);
        assertTrue(Files.isRegularFile(path), "missing " + path);
        String source = Files.readString(path);
        assertTrue(source.contains("\"replace\": false"));
        assertTrue(source.contains("\"sparkwitch:feather_blade\""));
        assertTrue(source.contains("\"sparkwitch:ninja_knife\""));
        assertFalse(source.contains("\"sparkwitch:ninja_shuriken\""));
    }
}
