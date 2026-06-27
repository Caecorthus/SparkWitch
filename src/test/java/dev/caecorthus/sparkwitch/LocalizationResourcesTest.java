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
            "item.sparkwitch.ceremonial_sword",
            "skill.sparkwitch.ceremonial_sword.name",
            "skill.sparkwitch.ceremonial_sword.description",
            "shop.sparkwitch.obscure",
            "shop.sparkwitch.blindness",
            "shop.sparkwitch.fear",
            "shop.sparkwitch.heaviness",
            "death_reason.sparkwitch.ceremonial_blade",
            "replay.death.sparkwitch.ceremonial_blade.killed",
            "replay.death.sparkwitch.ceremonial_blade.died",
            "gui.sparkwitch.skills",
            "gui.sparkwitch.mana",
            "gui.sparkwitch.shop.mana_price",
            "gui.sparkwitch.skill.cooldown",
            "gui.sparkwitch.skill.active",
            "gui.sparkwitch.skill.ready",
            "game.tip.sparkwitch.witch_cohort",
            "key.sparkwitch.skill",
            "message.sparkwitch.skill.no_skill",
            "message.sparkwitch.skill.not_witch",
            "message.sparkwitch.skill.dead",
            "message.sparkwitch.skill.cooldown",
            "message.sparkwitch.skill.unavailable",
            "message.sparkwitch.skill.unknown",
            "message.sparkwitch.skill.not_enough_mana",
            "message.sparkwitch.skill.ceremonial_sword.active",
            "message.sparkwitch.skill.ceremonial_sword.no_knife",
            "message.sparkwitch.skill.ceremonial_sword.activated",
            "message.sparkwitch.spell.obscure.cast",
            "message.sparkwitch.spell.obscure.actionbar",
            "message.sparkwitch.spell.blindness.cast",
            "message.sparkwitch.spell.fear.cast",
            "message.sparkwitch.spell.heaviness.cast",
            "shop.error.sparkwitch.not_enough_mana"
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
