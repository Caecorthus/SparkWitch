package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchMaidenResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/sparkwitch");

    @Test
    void shipsDistinctTransparentPixelArtAndGeneratedItemModels() throws IOException {
        assertItemResources("poison_apple");
        assertItemResources("tofana_elixir");
    }

    @Test
    void localizesIdentitySkillItemsAndExactTofanaDeathReason() throws IOException {
        JsonObject chinese = language("zh_cn");
        JsonObject english = language("en_us");

        assertEquals("巫女", value(chinese, "announcement.role.witch_maiden"));
        assertEquals("Witch Maiden", value(english, "announcement.role.witch_maiden"));
        assertEquals("聚焦步伐", value(chinese, "skill.sparkwitch.focused_footsteps.name"));
        assertEquals("Focused Footsteps", value(english, "skill.sparkwitch.focused_footsteps.name"));
        assertEquals("毒苹果", value(chinese, "item.sparkwitch.poison_apple"));
        assertEquals("托法娜仙液", value(chinese, "item.sparkwitch.tofana_elixir"));
        assertEquals(
                "我们的怒火永无止境，自由永远不会被束缚",
                value(chinese, "death_reason.sparkwitch.tofana_elixir")
        );
    }

    private static void assertItemResources(String id) throws IOException {
        Path modelPath = ASSETS.resolve("models/item/" + id + ".json");
        JsonObject model = JsonParser.parseString(Files.readString(modelPath)).getAsJsonObject();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("sparkwitch:item/" + id, model.getAsJsonObject("textures").get("layer0").getAsString());

        BufferedImage texture = ImageIO.read(ASSETS.resolve("textures/item/" + id + ".png").toFile());
        assertNotNull(texture);
        assertEquals(32, texture.getWidth());
        assertEquals(32, texture.getHeight());
        assertTrue(texture.getColorModel().hasAlpha());
        assertEquals(0, texture.getRGB(0, 0) >>> 24);
    }

    private static JsonObject language(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + locale + ".json")))
                .getAsJsonObject();
    }

    private static String value(JsonObject language, String key) {
        assertTrue(language.has(key), () -> "Missing translation: " + key);
        return language.get(key).getAsString();
    }
}
