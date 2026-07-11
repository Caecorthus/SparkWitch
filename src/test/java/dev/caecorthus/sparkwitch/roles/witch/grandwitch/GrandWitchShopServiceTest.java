package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchShopServiceTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/witch/grandwitch/GrandWitchShopService.java"
    );

    @Test
    void shopContainsAnOrdinaryUnlimitedRevolverForThreeHundredCoins() throws IOException {
        String source = Files.readString(SOURCE);
        String entryMarker = "new ShopEntry.Builder(\"revolver\"";
        int blockStart = source.indexOf(entryMarker);

        assertTrue(blockStart >= 0,
                "GrandWitchShopService must define a ShopEntry builder for entry id 'revolver'");

        int blockEnd = source.indexOf(".build()", blockStart);
        assertTrue(blockEnd >= 0,
                "The Grand Witch revolver ShopEntry builder must end with .build()");

        String builderBlock = source.substring(blockStart, blockEnd + ".build()".length());
        String normalizedBlock = builderBlock.replaceAll("\\s+", " ");
        assertTrue(normalizedBlock.contains(
                        "new ShopEntry.Builder(\"revolver\", WatheItems.REVOLVER.getDefaultStack(), 300, ShopEntry.Type.WEAPON)"
                ),
                () -> "The revolver entry must use Wathe's default revolver stack, price 300, and WEAPON type; found: "
                        + normalizedBlock);
        assertFalse(builderBlock.contains(".stock("),
                "The Grand Witch revolver entry must not set stock");
        assertFalse(builderBlock.contains(".cooldown("),
                "The Grand Witch revolver entry must not set a cooldown");
        assertFalse(builderBlock.contains(".onBuy("),
                "The Grand Witch revolver entry must use Wathe's ordinary purchase behavior");
    }
}
