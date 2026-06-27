package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/sparkwitch/lang");
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "faction.sparkwitch.witch",
            "announcement.role.grand_witch",
            "announcement.title.grand_witch",
            "announcement.goal.grand_witch",
            "announcement.goals.grand_witch",
            "announcement.win.grand_witch",
            "announcement.role.sparkwitch.grand_witch",
            "announcement.role.accomplice",
            "announcement.title.accomplice",
            "announcement.goal.accomplice",
            "announcement.goals.accomplice",
            "announcement.win.accomplice",
            "announcement.role.sparkwitch.accomplice",
            "announcement.role.apprentice_witch",
            "announcement.title.apprentice_witch",
            "announcement.goal.apprentice_witch",
            "announcement.goals.apprentice_witch",
            "announcement.win.apprentice_witch",
            "announcement.role.sparkwitch.apprentice_witch",
            "announcement.role.murderous_witch",
            "announcement.title.murderous_witch",
            "announcement.goal.murderous_witch",
            "announcement.goals.murderous_witch",
            "announcement.win.murderous_witch",
            "announcement.role.sparkwitch.murderous_witch",
            "item.sparkwitch.ceremonial_sword",
            "skill.sparkwitch.ceremonial_sword.name",
            "skill.sparkwitch.ceremonial_sword.description",
            "skill.sparkwitch.mighty_force.name",
            "skill.sparkwitch.mighty_force.description",
            "skill.sparkwitch.swift_step.name",
            "skill.sparkwitch.swift_step.description",
            "skill.sparkwitch.murder_sense.name",
            "skill.sparkwitch.murder_sense.description",
            "skill.sparkwitch.healing.name",
            "skill.sparkwitch.healing.description",
            "skill.sparkwitch.clairvoyance.name",
            "skill.sparkwitch.clairvoyance.description",
            "shop.sparkwitch.obscure",
            "shop.sparkwitch.blindness",
            "shop.sparkwitch.fear",
            "shop.sparkwitch.heaviness",
            "death_reason.sparkwitch.ceremonial_blade",
            "death_reason.sparkwitch.mighty_force",
            "replay.death.sparkwitch.ceremonial_blade.killed",
            "replay.death.sparkwitch.ceremonial_blade.died",
            "replay.death.sparkwitch.mighty_force.killed",
            "replay.death.sparkwitch.mighty_force.died",
            "gui.sparkwitch.skills",
            "gui.sparkwitch.mana",
            "gui.sparkwitch.shop.mana_price",
            "gui.sparkwitch.skill.cooldown",
            "gui.sparkwitch.skill.active",
            "gui.sparkwitch.skill.ready",
            "hud.sparkwitch.skill.cooldown",
            "hud.sparkwitch.skill.active",
            "hud.sparkwitch.skill.ready",
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
            "message.sparkwitch.skill.mighty_force.activated",
            "message.sparkwitch.skill.swift_step.activated",
            "message.sparkwitch.skill.murder_sense.activated",
            "message.sparkwitch.skill.healing.activated",
            "message.sparkwitch.skill.clairvoyance.activated",
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

    @Test
    void sparkWitchDoesNotDeclareIndependentAbilityKey() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        assertFalse(english.has("key.sparkwitch.ability"));
        assertFalse(chinese.has("key.sparkwitch.ability"));
    }

    @Test
    void watheAnnouncementAndReplayKeysUseRolePathWithoutNamespace() throws IOException {
        JsonObject chinese = readLang("zh_cn.json");

        assertEquals("大魔女", chinese.get("announcement.role.grand_witch").getAsString());
        assertEquals("共犯", chinese.get("announcement.role.accomplice").getAsString());
        assertEquals("运用你的魔力让人类血流成河", chinese.get("announcement.goal.grand_witch").getAsString());
        assertEquals("运用你的魔力让人类血流成河", chinese.get("announcement.goals.grand_witch").getAsString());
        assertEquals("协助大魔女完成她的目标", chinese.get("announcement.goal.accomplice").getAsString());
        assertEquals("协助大魔女完成她的目标", chinese.get("announcement.goals.accomplice").getAsString());
    }

    private static JsonObject readLang(String fileName) throws IOException {
        return JsonParser.parseString(Files.readString(LANG_DIR.resolve(fileName))).getAsJsonObject();
    }
}
