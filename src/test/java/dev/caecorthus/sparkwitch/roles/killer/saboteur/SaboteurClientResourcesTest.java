package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.skill.WitchSkillPresentationRules;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurClientResourcesTest {
    private static final Identifier SABOTAGE_SKILL_ID = Identifier.of("sparkwitch", "sabotage");
    private static final Path ASSETS = Path.of("src/main/resources/assets/sparkwitch");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void localizesExactSabotageHudStatesAndAbilityDetails() throws IOException {
        JsonObject chinese = language("zh_cn");
        JsonObject english = language("en_us");

        assertEquals("破坏", value(chinese, "skill.sparkwitch.sabotage.name"));
        assertEquals("Sabotage", value(english, "skill.sparkwitch.sabotage.name"));
        assertEquals("技能冷却 %s 秒", value(chinese, "hud.sparkwitch.skill.sabotage.cooldown"));
        assertEquals("按【%s】使用破坏", value(chinese, "hud.sparkwitch.skill.sabotage.ready"));
        assertEquals("Skill cooldown: %s seconds", value(english, "hud.sparkwitch.skill.sabotage.cooldown"));
        assertEquals("Press [%s] to use Sabotage", value(english, "hud.sparkwitch.skill.sabotage.ready"));

        for (JsonObject language : new JsonObject[]{chinese, english}) {
            String description = value(language, "skill.sparkwitch.sabotage.description");
            assertTrue(description.contains("20"));
            assertTrue(description.contains("60"));
            assertTrue(description.contains("120"));
        }
    }

    @Test
    void announcementStatesOnlyTheApprovedSaboteurEconomyShopAndSkill() throws IOException {
        JsonObject chinese = language("zh_cn");
        JsonObject english = language("en_us");

        assertEquals(
                "消灭好人阵营。晋升后每完成一项任务获得 50 金币。专属商店只出售一个 50 金币的开锁器，以及保持原价的熄灯。破坏在晋升时先冷却 60 秒；使用后立刻令 20 格内所有可熄灭列车灯熄灭 20 秒，之后冷却 120 秒。",
                value(chinese, "announcement.goals.saboteur")
        );
        assertEquals(
                "Eliminate the passengers. Each task completed after promotion grants 50 coins. Your private shop contains only one lockpick for 50 coins and the standard blackout at its original price. Sabotage starts with a 60-second cooldown; use it to black out all eligible train lights within 20 blocks for 20 seconds, then it cools down for 120 seconds.",
                value(english, "announcement.goals.saboteur")
        );

        String combined = value(chinese, "announcement.goals.saboteur")
                + value(english, "announcement.goals.saboteur");
        assertFalse(combined.contains("无限体力"));
        assertFalse(combined.contains("熄灯免疫"));
        assertFalse(combined.contains("unlimited stamina"));
        assertFalse(combined.contains("blackout immunity"));
    }

    @Test
    void sabotageNeverUsesTheWitchInventorySkillPanel() {
        Role saboteur = SparkFactionApi.getRolesForFaction(FactionIds.KILLER).stream()
                .filter(role -> SaboteurRole.ID.equals(role.identifier()))
                .findFirst()
                .orElseThrow();

        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(saboteur, SABOTAGE_SKILL_ID));
    }

    private static JsonObject language(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + locale + ".json")))
                .getAsJsonObject();
    }

    private static String value(JsonObject language, String key) {
        assertTrue(language.has(key), () -> "Missing translation: " + key);
        return language.get(key).getAsString();
    }
}
