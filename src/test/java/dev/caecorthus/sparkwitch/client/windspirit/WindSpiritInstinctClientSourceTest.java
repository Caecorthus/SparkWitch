package dev.caecorthus.sparkwitch.client.windspirit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindSpiritInstinctClientSourceTest {
    private static final Path CLIENT_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client"
    );

    @Test
    void rulesGrantNativeInstinctOnlyToSparkWitchWindSpirit() throws IOException {
        String rules = read(CLIENT_ROOT.resolve("windspirit/WindSpiritInstinctClientRules.java"));

        assertTrue(rules.contains("WindSpiritRole.ID.equals(roleId)"));
        assertTrue(rules.contains("confirmedServer"));
        assertTrue(rules.contains("viewerPlayingAndAlive || promotedWraith"));
        assertTrue(rules.contains("instinctKeyPressed"));
        assertFalse(rules.contains("sparktraits"));
    }

    @Test
    void mixinUsesWatheNativeHighlightGateAndPreservesPriorityVetoes() throws IOException {
        String mixin = read(CLIENT_ROOT.resolve("mixin/windspirit/WindSpiritInstinctMixin.java"));
        String wraithHighlight = read(CLIENT_ROOT.resolve("mixin/wraith/WraithWatheHighlightMixin.java"));
        String fear = read(CLIENT_ROOT.resolve("mixin/WatheClientFearInstinctMixin.java"));
        String blackRaven = read(CLIENT_ROOT.resolve("mixin/blackraven/BlackRavenInstinctGateMixin.java"));

        assertTrue(mixin.contains("method = \"getInstinctHighlight\""));
        assertTrue(mixin.contains("WatheClient;isKiller()Z"));
        assertTrue(mixin.contains("method = \"isInstinctEnabledAndIsKiller\""));
        assertTrue(mixin.contains("WraithClientState.isPromoted(player)"));
        assertTrue(mixin.contains("priority = 1000"));
        assertTrue(wraithHighlight.contains("priority = 2000"));
        assertTrue(fear.contains("priority = 1500"));
        assertTrue(blackRaven.contains("priority = 1600"));
    }

    @Test
    void clientConfigRegistersWindSpiritMixinAndExistingLightmapWrapper() throws IOException {
        String config = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));
        String lightmap = read(CLIENT_ROOT.resolve("mixin/WatheClientInstinctLightMixin.java"));

        assertTrue(config.contains("windspirit.WindSpiritInstinctMixin"));
        assertTrue(config.contains("WatheClientInstinctLightMixin"));
        assertTrue(lightmap.contains("WatheClient;isInstinctEnabledAndIsKiller()Z"));
        assertTrue(lightmap.contains("original.call()"));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
