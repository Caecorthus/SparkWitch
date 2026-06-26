package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/sparkwitch/lang");
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "faction.sparkwitch.witch",
            "announcement.role.sparkwitch.grand_witch",
            "announcement.role.sparkwitch.accomplice",
            "announcement.role.sparkwitch.apprentice_witch",
            "announcement.role.sparkwitch.murderous_witch",
            "item.sparkwitch.ritual_sword",
            "death_reason.sparkwitch.ritual_blade",
            "replay.death.sparkwitch.ritual_blade.killed",
            "replay.death.sparkwitch.ritual_blade.died",
            "gui.sparkwitch.skills",
            "gui.sparkwitch.skill.cooldown",
            "gui.sparkwitch.skill.ready",
            "key.sparkwitch.skill",
            "message.sparkwitch.skill.no_skill",
            "message.sparkwitch.skill.not_witch",
            "message.sparkwitch.skill.dead",
            "message.sparkwitch.skill.cooldown",
            "message.sparkwitch.skill.unavailable",
            "message.sparkwitch.skill.unknown"
    );

    @Test
    void englishAndChineseResourcesHaveMatchingKeys() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        assertEquals(english.keySet(), chinese.keySet());
        for (String key : REQUIRED_KEYS) {
            assertTrue(english.has(key), key);
            assertTrue(chinese.has(key), key);
        }
    }

    private static JsonObject readLang(String fileName) throws IOException {
        return JsonParser.parseString(Files.readString(LANG_DIR.resolve(fileName))).getAsJsonObject();
    }
}
