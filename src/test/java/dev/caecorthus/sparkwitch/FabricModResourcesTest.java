package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricModResourcesTest {
    private static final Path FABRIC_MOD_JSON = Path.of("src/main/resources/fabric.mod.json");

    @Test
    void noLongerDeclaresMigratedFlashlightDynamicLightsEntrypoint() throws IOException {
        JsonObject mod = JsonParser.parseString(Files.readString(FABRIC_MOD_JSON)).getAsJsonObject();
        JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
        JsonObject depends = mod.getAsJsonObject("depends");

        assertFalse(entrypoints.has("lambdynlights:initializer"));
        assertFalse(entrypoints.has("dynamiclights"));
        assertFalse(depends.has("lambdynlights"));
    }

    @Test
    void noLongerDeclaresMigratedRoleEnhancementComponent() throws IOException {
        JsonObject mod = JsonParser.parseString(Files.readString(FABRIC_MOD_JSON)).getAsJsonObject();

        assertEquals(
                "sparkwitch:player",
                mod.getAsJsonObject("custom").getAsJsonArray("cardinal-components").get(0).getAsString()
        );
        assertFalse(Files.readString(FABRIC_MOD_JSON).contains("sparkwitch:role_enhancements"));
    }

    @Test
    void declaresPinnedRuntimeDependencyVersions() throws IOException {
        JsonObject mod = JsonParser.parseString(Files.readString(FABRIC_MOD_JSON)).getAsJsonObject();
        JsonObject depends = mod.getAsJsonObject("depends");

        assertEquals("${wathe_version}", depends.get("wathe").getAsString());
        assertEquals("${noellesroles_version}", depends.get("noellesroles").getAsString());
        assertEquals(">=${sparkfactionapi_version}", depends.get("sparkfactionapi").getAsString());
        assertEquals("${ratatouille_version}", depends.get("ratatouille").getAsString());
        assertTrue(depends.has("cardinal-components-base"));
    }
}
