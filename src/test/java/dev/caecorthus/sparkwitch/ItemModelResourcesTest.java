package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelResourcesTest {
    private static final Path MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/ceremonial_sword.json");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/sparkwitch/textures/item/ceremonial_sword.png");
    private static final Path MANA_FONT_TEXTURE = Path.of("src/main/resources/assets/sparkwitch/textures/font/mana.png");
    private static final Path DEFAULT_FONT = Path.of("src/main/resources/assets/minecraft/font/default.json");

    @Test
    void ceremonialSwordModelUsesSparkWitchTexture() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();

        assertEquals("sparkwitch:item/ceremonial_sword", model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void ceremonialSwordModelResourceExists() throws IOException {
        assertTrue(Files.isRegularFile(MODEL));
        assertNotNull(ImageIO.read(TEXTURE.toFile()));
    }

    @Test
    void manaFontResourceExistsWithoutClaimingWatheCoinGlyph() throws IOException {
        String fontJson = Files.readString(DEFAULT_FONT);

        assertNotNull(ImageIO.read(MANA_FONT_TEXTURE.toFile()));
        assertTrue(fontJson.contains("\\uE782"));
        assertTrue(fontJson.contains("sparkwitch:font/mana.png"));
        assertFalse(fontJson.contains("\\uE781"));
    }
}
