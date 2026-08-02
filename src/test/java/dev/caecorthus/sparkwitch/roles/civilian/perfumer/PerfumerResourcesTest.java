package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfumerResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/sparkwitch");

    @Test
    void shipsApprovedItemModelsAndDetailedThirtyTwoPixelTextures() throws IOException {
        assertItemResource("perfume_essence");
        assertItemResource("cologne");
    }

    @Test
    void localizesTheRoleItemsAndPrivateFeedback() throws IOException {
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));

        assertTrue(chinese.contains("\"announcement.role.perfumer\": \"调香师\""));
        assertTrue(chinese.contains("\"item.sparkwitch.perfume_essence\": \"香精\""));
        assertTrue(chinese.contains("\"item.sparkwitch.cologne\": \"古龙水\""));
        assertTrue(chinese.contains("message.sparkwitch.perfumer.marked"));
        assertTrue(chinese.contains("message.sparkwitch.cologne.received"));
        assertTrue(chinese.contains("\"announcement.goals.perfumer\": \"标记嫌疑人、追踪血腥气味并帮助好人。\""));

        assertTrue(english.contains("\"announcement.role.perfumer\": \"Perfumer\""));
        assertTrue(english.contains("\"item.sparkwitch.perfume_essence\": \"Perfume Essence\""));
        assertTrue(english.contains("\"item.sparkwitch.cologne\": \"Cologne\""));
        assertTrue(english.contains("\"announcement.goals.perfumer\": \"Mark suspects, follow bloody scents, and aid the passengers.\""));
    }

    private static void assertItemResource(String itemId) throws IOException {
        Path modelPath = ASSETS.resolve("models/item/" + itemId + ".json");
        Path texturePath = ASSETS.resolve("textures/item/" + itemId + ".png");
        String model = Files.readString(modelPath);
        BufferedImage texture = ImageIO.read(texturePath.toFile());

        assertTrue(model.contains("sparkwitch:item/" + itemId));
        assertNotNull(texture);
        assertEquals(32, texture.getWidth());
        assertEquals(32, texture.getHeight());
        assertTrue(texture.getColorModel().hasAlpha());
    }
}
