package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarotReaderItemResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/sparkwitch");

    @Test
    void registersOneTarotCardAndUsesItForAllThreeDivinations() throws IOException {
        String items = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/SparkWitchItems.java"
        ));
        String shop = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/tarotreader/TarotReaderShopService.java"
        ));

        assertTrue(items.contains("TAROT_CARD_ID = SparkWitch.id(\"tarot_card\")"));
        assertTrue(items.contains("public static Item tarotCard()"));
        assertEquals(3, occurrences(shop, "SparkWitchItems.tarotCard()"));
    }

    @Test
    void shipsDetailedTransparentThirtyTwoPixelTarotCardArt() throws IOException {
        Path modelPath = ASSETS.resolve("models/item/tarot_card.json");
        Path texturePath = ASSETS.resolve("textures/item/tarot_card.png");
        assertTrue(Files.isRegularFile(modelPath));
        assertTrue(Files.isRegularFile(texturePath));
        String model = Files.readString(modelPath);
        BufferedImage texture = ImageIO.read(texturePath.toFile());

        assertTrue(model.contains("minecraft:item/generated"));
        assertTrue(model.contains("sparkwitch:item/tarot_card"));
        assertNotNull(texture);
        assertEquals(32, texture.getWidth());
        assertEquals(32, texture.getHeight());
        assertTrue(texture.getColorModel().hasAlpha());

        int opaquePixels = 0;
        int transparentPixels = 0;
        Set<Integer> opaqueColors = new HashSet<>();
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int argb = texture.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    transparentPixels++;
                } else {
                    opaquePixels++;
                    opaqueColors.add(argb & 0xFFFFFF);
                }
            }
        }
        assertTrue(opaquePixels >= 180);
        assertTrue(transparentPixels >= 200);
        assertTrue(opaqueColors.size() >= 8);
    }

    @Test
    void localizesTheTarotCardInBothLocales() throws IOException {
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));

        assertTrue(chinese.contains("\"item.sparkwitch.tarot_card\": \"塔罗牌\""));
        assertTrue(english.contains("\"item.sparkwitch.tarot_card\": \"Tarot Card\""));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
