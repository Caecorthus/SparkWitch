package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/sparkwitch/lang");
    private static final String MANA_GLYPH = "\uE782";
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "faction.sparkwitch.witch",
            "announcement.role.the_insane_damned_paranoid_killer",
            "announcement.role.grand_witch",
            "announcement.title.grand_witch",
            "announcement.goal.grand_witch",
            "announcement.goals.grand_witch",
            "announcement.win.grand_witch",
            "announcement.win.sparkwitch.witch",
            "game.win.grand_witch",
            "game.win.sparkwitch.witch",
            "announcement.role.sparkwitch.grand_witch",
            "announcement.role.accomplice",
            "announcement.title.accomplice",
            "announcement.goal.accomplice",
            "announcement.goals.accomplice",
            "announcement.win.accomplice",
            "game.win.accomplice",
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
            "game.win.murderous_witch",
            "announcement.role.sparkwitch.murderous_witch",
            "announcement.role.pig_god",
            "announcement.title.pig_god",
            "announcement.goal.pig_god",
            "announcement.goals.pig_god",
            "announcement.win.pig_god",
            "announcement.role.sparkwitch.pig_god",
            "announcement.goal.sparkwitch.pig_god",
            "announcement.win.sparkwitch.pig_god",
            "item.sparkwitch.ceremonial_sword",
            "item.sparkwitch.fire_poker",
            "item.sparkwitch.capsule",
            "item.sparkwitch.capsule.filled",
            "item.sparkwitch.capsule.poisoned_content",
            "item.sparkwitch.capsule.tooltip",
            "item.sparkwitch.flashlight",
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
            "skill.sparkwitch.pig_chase.name",
            "skill.sparkwitch.pig_chase.description",
            "skill.sparkwitch.death_ray.name",
            "skill.sparkwitch.death_ray.description",
            "shop.sparkwitch.obscure",
            "shop.sparkwitch.obscure.description",
            "shop.sparkwitch.blindness",
            "shop.sparkwitch.blindness.description",
            "shop.sparkwitch.fear",
            "shop.sparkwitch.fear.description",
            "shop.sparkwitch.heaviness",
            "shop.sparkwitch.heaviness.description",
            "shop.sparkwitch.capsule",
            "shop.sparkwitch.capsule.description",
            "death_reason.sparkwitch.ceremonial_blade",
            "death_reason.sparkwitch.mighty_force",
            "death_reason.sparkwitch.pierced_by_ray",
            "replay.death.sparkwitch.ceremonial_blade.killed",
            "replay.death.sparkwitch.ceremonial_blade.died",
            "replay.death.sparkwitch.mighty_force.killed",
            "replay.death.sparkwitch.mighty_force.died",
            "replay.death.sparkwitch.pierced_by_ray.killed",
            "replay.death.sparkwitch.pierced_by_ray.died",
            "subtitles.sparkwitch.skill.pig_chase",
            "gui.sparkwitch.skills",
            "gui.sparkwitch.mana",
            "gui.sparkwitch.shop.mana_price",
            "gui.sparkwitch.skill.cooldown",
            "gui.sparkwitch.skill.active",
            "gui.sparkwitch.skill.ceremonial_sword.locked",
            "gui.sparkwitch.skill.ready",
            "hud.sparkwitch.skill.cooldown",
            "hud.sparkwitch.skill.active",
            "hud.sparkwitch.skill.death_ray.active",
            "hud.sparkwitch.skill.ceremonial_sword.locked",
            "hud.sparkwitch.skill.pig_chase.not_enough_money",
            "hud.sparkwitch.skill.not_enough_mana",
            "hud.sparkwitch.skill.ready",
            "hud.sparkwitch.criminologist.cooldown",
            "hud.sparkwitch.criminologist.ready",
            "hud.sparkwitch.criminologist.tracking",
            "screen.sparkwitch.criminologist.title",
            "screen.sparkwitch.criminologist.subtitle",
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
            "message.sparkwitch.skill.ceremonial_sword.locked",
            "message.sparkwitch.skill.ceremonial_sword.no_knife",
            "message.sparkwitch.skill.ceremonial_sword.activated",
            "message.sparkwitch.skill.mighty_force.activated",
            "message.sparkwitch.skill.swift_step.activated",
            "message.sparkwitch.skill.murder_sense.activated",
            "message.sparkwitch.skill.healing.activated",
            "message.sparkwitch.skill.clairvoyance.activated",
            "message.sparkwitch.skill.pig_chase.active",
            "message.sparkwitch.skill.pig_chase.not_enough_money",
            "message.sparkwitch.skill.pig_chase.no_inventory_space",
            "message.sparkwitch.skill.pig_chase.activated",
            "message.sparkwitch.skill.death_ray.active",
            "message.sparkwitch.skill.death_ray.activated",
            "message.sparkwitch.criminologist.cooldown",
            "message.sparkwitch.criminologist.not_enough_money",
            "message.sparkwitch.criminologist.already_tracking",
            "message.sparkwitch.criminologist.pending",
            "message.sparkwitch.criminologist.no_pending",
            "message.sparkwitch.criminologist.wrong",
            "message.sparkwitch.criminologist.correct",
            "message.sparkwitch.criminologist.killer_dead",
            "message.sparkwitch.capsule.empty",
            "message.sparkwitch.flashlight.on",
            "message.sparkwitch.flashlight.off",
            "message.sparkwitch.spell.obscure.cast",
            "message.sparkwitch.spell.obscure.actionbar",
            "message.sparkwitch.spell.blindness.cast",
            "message.sparkwitch.spell.fear.cast",
            "message.sparkwitch.spell.heaviness.cast",
            "message.sparkwitch.fear.skill_blocked",
            "message.sparkwitch.fear.instinct_blocked",
            "shop.error.sparkwitch.not_enough_mana",
            "shop.error.sparkwitch.fear"
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
    void corpseRoleKeysMatchWatheHudConvention() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        for (Role role : List.of(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.apprenticeWitch(),
                SparkWitchRoles.murderousWitch(),
                SparkWitchRoles.pigGod()
        )) {
            String key = RoleDisplayTextRules.roleTranslationKey(role);
            assertTrue(english.has(key), key);
            assertTrue(chinese.has(key), key);
        }
    }

    @Test
    void compatibilityRoleAliasCoversLongNoellesRoleInChinese() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        assertEquals(
                "The Insane Damned Paranoid Killer Of Doom Death Destruction And Waffles",
                english.get("announcement.role.the_insane_damned_paranoid_killer").getAsString()
        );
        assertEquals("亡语杀手", chinese.get("announcement.role.the_insane_damned_paranoid_killer").getAsString());
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
        assertEquals("杀死其他所有人。", chinese.get("announcement.goal.murderous_witch").getAsString());
        assertEquals("杀死其他所有人。", chinese.get("announcement.goals.murderous_witch").getAsString());
        assertEquals("皮革噶的", chinese.get("announcement.role.pig_god").getAsString());
        assertEquals("帮助好人阵营，等待皮革追杀的时机。", chinese.get("announcement.goal.pig_god").getAsString());
        assertEquals("帮助好人阵营，等待皮革追杀的时机。", chinese.get("announcement.goals.pig_god").getAsString());
    }

    @Test
    void witchRoundEndTextUsesShortTitleAndVengeanceReason() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        for (String titleKey : List.of(
                "announcement.win.grand_witch",
                "announcement.win.accomplice",
                "announcement.win.sparkwitch.witch",
                "announcement.win.sparkwitch.grand_witch",
                "announcement.win.sparkwitch.accomplice"
        )) {
            assertEquals("Witch Victory", english.get(titleKey).getAsString(), titleKey);
            assertEquals("魔女胜利", chinese.get(titleKey).getAsString(), titleKey);
        }

        for (String reasonKey : List.of(
                "game.win.grand_witch",
                "game.win.accomplice",
                "game.win.sparkwitch.witch"
        )) {
            assertEquals("The witch completed her revenge.", english.get(reasonKey).getAsString(), reasonKey);
            assertEquals("魔女完成了她的复仇", chinese.get(reasonKey).getAsString(), reasonKey);
        }

        assertEquals("Murderous Witch Victory", english.get("announcement.win.murderous_witch").getAsString());
        assertEquals("杀意魔女胜利", chinese.get("announcement.win.murderous_witch").getAsString());
        assertEquals("The craving for blood has finally been satisfied...", english.get("game.win.murderous_witch").getAsString());
        assertEquals("对鲜血的渴望终于得到了满足...", chinese.get("game.win.murderous_witch").getAsString());
        assertEquals("The passengers survived.", english.get("announcement.win.pig_god").getAsString());
        assertEquals("好人幸存了下来。", chinese.get("announcement.win.pig_god").getAsString());
    }

    @Test
    void manaHudShopPriceAndSkillRequirementUseIconWithoutManaWords() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        assertManaIconText(english.get("gui.sparkwitch.mana").getAsString());
        assertManaIconText(english.get("gui.sparkwitch.shop.mana_price").getAsString());
        assertManaIconText(english.get("hud.sparkwitch.skill.not_enough_mana").getAsString());
        assertManaIconText(chinese.get("gui.sparkwitch.mana").getAsString());
        assertManaIconText(chinese.get("gui.sparkwitch.shop.mana_price").getAsString());
        assertManaIconText(chinese.get("hud.sparkwitch.skill.not_enough_mana").getAsString());
    }

    @Test
    void flashlightDoesNotDeclareUnavailableText() throws IOException {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");

        assertFalse(english.has("message.sparkwitch.flashlight.unavailable"));
        assertFalse(chinese.has("message.sparkwitch.flashlight.unavailable"));
    }

    private static JsonObject readLang(String fileName) throws IOException {
        return JsonParser.parseString(Files.readString(LANG_DIR.resolve(fileName))).getAsJsonObject();
    }

    private static void assertManaIconText(String text) {
        assertTrue(text.contains(MANA_GLYPH), text);
        assertFalse(text.contains("Mana"), text);
        assertFalse(text.contains("mana"), text);
        assertFalse(text.contains("魔力"), text);
        assertFalse(text.contains("魔力值"), text);
    }
}
