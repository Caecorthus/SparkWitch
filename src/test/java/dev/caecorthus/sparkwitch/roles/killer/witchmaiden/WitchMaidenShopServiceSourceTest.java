package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WitchMaidenShopServiceSourceTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/witchmaiden/WitchMaidenShopService.java"
    );

    @Test
    void replacesOnlyWitchMaidenShopAndRetainsCapturedBlackout() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("if (!WitchMaidenRules.isWitchMaiden(role))"));
        int capture = source.indexOf("\"blackout\".equals(entry.id())");
        int clear = source.indexOf("context.clearEntries()");
        int restore = source.indexOf("context.addEntry(blackoutEntry)");
        assertTrue(capture >= 0 && clear > capture && restore > clear);

        assertEntry(source, "knife", "WitchMaidenRules.KNIFE_PRICE", true);
        assertEntry(source, "lockpick", "WitchMaidenRules.LOCKPICK_PRICE", true);
        assertEntry(source, "poison_vial", "WitchMaidenRules.POISON_PRICE", false);
        assertEntry(source, "scorpion", "WitchMaidenRules.POISON_PRICE", false);
        assertEntry(source, "poison_apple", "WitchMaidenRules.POISON_PRICE", false);
        assertEntry(source, "tofana_elixir", "WitchMaidenRules.TOFANA_PRICE", true);
    }

    private static void assertEntry(String source, String id, String price, boolean stockOne) {
        int start = source.indexOf("\"" + id + "\"");
        int end = source.indexOf(".build()", start);
        String entry = source.substring(start, end);
        assertTrue(entry.contains(price));
        assertEquals(stockOne, entry.contains(".stock(1)"));
    }
}
