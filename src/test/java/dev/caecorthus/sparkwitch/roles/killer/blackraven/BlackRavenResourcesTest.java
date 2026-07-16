package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BlackRavenResourcesTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void registersBothSeparateComponentsAndMixins() throws IOException {
        String fabric = Files.readString(ROOT.resolve("src/main/resources/fabric.mod.json"));
        String mixins = Files.readString(ROOT.resolve("src/main/resources/sparkwitch.mixins.json"));
        assertTrue(fabric.contains("sparkwitch:black_raven_mark"));
        assertTrue(fabric.contains("sparkwitch:black_raven_perception"));
        assertTrue(mixins.contains("NoellesHiddenEquipmentBlackRavenLedgerMixin"));
        assertTrue(mixins.contains("GameFunctionsBlackRavenDropMixin"));
    }

    @Test
    void contributesOnlyFeatherBladeToBloodthirstyTag() throws IOException {
        String tag = Files.readString(ROOT.resolve(
                "src/main/resources/data/sparktraits/tags/item/bloodthirsty_weapons.json"
        ));
        assertTrue(tag.contains("\"replace\": false"));
        assertTrue(tag.contains("sparkwitch:feather_blade"));
    }

    @Test
    void hasLedgerModelAndBothLocalizedItemNames() throws IOException {
        assertTrue(Files.exists(ROOT.resolve("src/main/resources/assets/sparkwitch/models/item/black_raven_ledger.json")));
        for (String language : new String[]{"en_us", "zh_cn"}) {
            String json = Files.readString(ROOT.resolve("src/main/resources/assets/sparkwitch/lang/" + language + ".json"));
            assertTrue(json.contains("item.sparkwitch.feather_blade"));
            assertTrue(json.contains("item.sparkwitch.black_raven_ledger"));
            assertTrue(json.contains("skill.sparkwitch.perception.name"));
        }
    }
}
