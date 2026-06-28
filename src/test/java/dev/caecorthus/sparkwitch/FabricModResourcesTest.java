package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricModResourcesTest {
    private static final Path FABRIC_MOD_JSON = Path.of("src/main/resources/fabric.mod.json");
    private static final String FLASHLIGHT_INITIALIZER = "dev.caecorthus.sparkwitch.client.FlashlightDynamicLightsInitializer";

    @Test
    void declaresLambDynamicLightsForFlashlightLineLight() throws IOException {
        JsonObject mod = JsonParser.parseString(Files.readString(FABRIC_MOD_JSON)).getAsJsonObject();
        JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
        JsonObject depends = mod.getAsJsonObject("depends");

        assertEquals(FLASHLIGHT_INITIALIZER, entrypoints.getAsJsonArray("lambdynlights:initializer").get(0).getAsString());
        assertEquals(FLASHLIGHT_INITIALIZER, entrypoints.getAsJsonArray("dynamiclights").get(0).getAsString());
        assertTrue(depends.get("lambdynlights").getAsString().contains("4.8.7+1.21.1"));
    }
}
