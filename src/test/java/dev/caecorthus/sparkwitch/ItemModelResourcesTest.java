package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkwitch.item.FlashlightItem;
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
    private static final Path CEREMONIAL_SWORD_MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/ceremonial_sword.json");
    private static final Path CEREMONIAL_SWORD_TEXTURE = Path.of("src/main/resources/assets/sparkwitch/textures/item/ceremonial_sword.png");
    private static final Path MANA_FONT_TEXTURE = Path.of("src/main/resources/assets/sparkwitch/textures/font/mana.png");
    private static final Path DEFAULT_FONT = Path.of("src/main/resources/assets/minecraft/font/default.json");
    private static final Path FIRE_POKER_MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/fire_poker.json");
    private static final Path FIRE_POKER_TEXTURE = Path.of("src/main/resources/assets/sparkwitch/textures/item/fire_poker.png");
    private static final Path CAPSULE_MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/capsule.json");
    private static final Path FLASHLIGHT_MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/flashlight.json");
    private static final Path FLASHLIGHT_ON_MODEL = Path.of("src/main/resources/assets/sparkwitch/models/item/flashlight_on.json");
    private static final Path FLASHLIGHT_DYNAMIC_LIGHT = Path.of("src/main/resources/assets/sparkwitch/dynamiclights/item/flashlight.json");

    @Test
    void ceremonialSwordModelUsesSparkWitchTexture() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(CEREMONIAL_SWORD_MODEL)).getAsJsonObject();

        assertEquals("sparkwitch:item/ceremonial_sword", model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void ceremonialSwordModelResourceExists() throws IOException {
        assertTrue(Files.isRegularFile(CEREMONIAL_SWORD_MODEL));
        assertNotNull(ImageIO.read(CEREMONIAL_SWORD_TEXTURE.toFile()));
    }

    @Test
    void manaFontResourceExistsWithoutClaimingWatheCoinGlyph() throws IOException {
        String fontJson = Files.readString(DEFAULT_FONT);

        assertNotNull(ImageIO.read(MANA_FONT_TEXTURE.toFile()));
        assertTrue(fontJson.contains("\\uE782"));
        assertTrue(fontJson.contains("sparkwitch:font/mana.png"));
        assertFalse(fontJson.contains("\\uE781"));
    }

    @Test
    void firePokerModelUsesBundledBurnStickTexture() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(FIRE_POKER_MODEL)).getAsJsonObject();

        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("sparkwitch:item/fire_poker", model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void firePokerModelResourceExists() throws IOException {
        assertTrue(Files.isRegularFile(FIRE_POKER_MODEL));
        assertNotNull(ImageIO.read(FIRE_POKER_TEXTURE.toFile()));
    }

    @Test
    void capsuleModelUsesVanillaPlaceholderTexture() throws IOException {
        JsonObject capsule = JsonParser.parseString(Files.readString(CAPSULE_MODEL)).getAsJsonObject();

        assertEquals("minecraft:item/generated", capsule.get("parent").getAsString());
        assertEquals("minecraft:item/snowball", capsule.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void flashlightModelsUseLanternStates() throws IOException {
        JsonObject flashlight = JsonParser.parseString(Files.readString(FLASHLIGHT_MODEL)).getAsJsonObject();
        JsonObject flashlightOn = JsonParser.parseString(Files.readString(FLASHLIGHT_ON_MODEL)).getAsJsonObject();
        JsonObject override = flashlight.getAsJsonArray("overrides").get(0).getAsJsonObject();

        assertEquals("minecraft:item/generated", flashlight.get("parent").getAsString());
        assertEquals("minecraft:item/soul_lantern", flashlight.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(FlashlightItem.ON_MODEL_DATA, override.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
        assertEquals("sparkwitch:item/flashlight_on", override.get("model").getAsString());
        assertEquals("minecraft:item/generated", flashlightOn.get("parent").getAsString());
        assertEquals("minecraft:item/lantern", flashlightOn.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void litFlashlightHasLambDynamicLightsSource() throws IOException {
        JsonObject lightSource = JsonParser.parseString(Files.readString(FLASHLIGHT_DYNAMIC_LIGHT)).getAsJsonObject();
        JsonObject match = lightSource.getAsJsonObject("match");

        assertEquals("sparkwitch:flashlight", match.getAsJsonArray("items").get(0).getAsString());
        assertEquals(FlashlightItem.ON_MODEL_DATA, match.getAsJsonObject("components").get("minecraft:custom_model_data").getAsInt());
        assertEquals(15, lightSource.get("luminance").getAsInt());
    }
}
