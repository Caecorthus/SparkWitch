package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardianAngelResourcesTest {
    @Test
    void localizesGuardianSkillHudEffectFailureAndReplayExactly() throws IOException {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");

        assertEquals("Guardian", value(english, "skill.sparkwitch.guardian.name"));
        assertEquals("After a 60-second initial cooldown, aim at a living player within 3 blocks and press the "
                        + "shared ability key to secretly apply one Guardian Shield for 10 seconds. A successful cast "
                        + "starts a 90-second cooldown.",
                value(english, "skill.sparkwitch.guardian.description"));
        assertEquals("Guardian cooling down: %ss", value(english, "hud.sparkwitch.guardian_angel.cooldown"));
        assertEquals("Aim at a player", value(english, "hud.sparkwitch.guardian_angel.no_target"));
        assertEquals("Press %s to apply a shield", value(english, "hud.sparkwitch.guardian_angel.ready"));
        assertEquals("Guardian Shield", value(english, "effect.sparkwitch.guardian_shield"));
        assertEquals("That player already has a Guardian Shield.",
                value(english, "message.sparkwitch.guardian_angel.already_shielded"));
        assertEquals("%s applied §aGuardian Shield§r to %s",
                value(english, "replay.skill.sparkwitch.guardian_angel.guardian"));
        assertEquals("%s's §aGuardian Shield§r saved %s",
                value(english, "replay.global.sparkwitch.guardian_shield_activated"));
        assertEquals("A §aGuardian Shield§r saved %s",
                value(english, "replay.global.sparkwitch.guardian_shield_activated.no_owner"));

        assertEquals("守护", value(chinese, "skill.sparkwitch.guardian.name"));
        assertEquals("晋升60秒后，瞄准三格内一名存活玩家并按下技能键，可秘密施加一层持续10秒、"
                        + "判定与铁人药剂相同的守护护盾；成功施放后冷却90秒。",
                value(chinese, "skill.sparkwitch.guardian.description"));
        assertEquals("守护冷却中：%s秒", value(chinese, "hud.sparkwitch.guardian_angel.cooldown"));
        assertEquals("瞄准一位玩家", value(chinese, "hud.sparkwitch.guardian_angel.no_target"));
        assertEquals("按 %s 施加护盾", value(chinese, "hud.sparkwitch.guardian_angel.ready"));
        assertEquals("守护护盾", value(chinese, "effect.sparkwitch.guardian_shield"));
        assertEquals("该玩家已有护盾",
                value(chinese, "message.sparkwitch.guardian_angel.already_shielded"));
        assertEquals("%s 为 %s 施加了§a守护护盾§r",
                value(chinese, "replay.skill.sparkwitch.guardian_angel.guardian"));
        assertEquals("%s 的§a守护护盾§r救下了 %s",
                value(chinese, "replay.global.sparkwitch.guardian_shield_activated"));
        assertEquals("§a守护护盾§r救下了 %s",
                value(chinese, "replay.global.sparkwitch.guardian_shield_activated.no_owner"));
    }

    private static JsonObject language(String locale) throws IOException {
        Path path = Path.of("src/main/resources/assets/sparkwitch/lang", locale + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String value(JsonObject language, String key) {
        return language.get(key).getAsString();
    }
}
