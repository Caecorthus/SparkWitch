package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelResourcesTest {
    private static final Path MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/ceremonial_sword.json");

    @Test
    void ceremonialSwordModelUsesHandheldIronSwordPlaceholder() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();

        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("minecraft:item/iron_sword", model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void ceremonialSwordModelResourceExists() {
        assertTrue(Files.isRegularFile(MODEL));
    }
}
