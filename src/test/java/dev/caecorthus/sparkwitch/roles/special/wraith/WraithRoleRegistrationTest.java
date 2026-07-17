package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRoleRegistrationTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void registersCanonicalWraithAsANonRollablePersistentSpecialRole() {
        Role wraith = SparkWitchRoles.wraith();

        assertEquals(Identifier.of("sparkwitch", "wraith"), wraith.identifier());
        assertEquals(0x79C7D4, wraith.color());
        assertEquals(Role.MoodType.NONE, wraith.getMoodType());
        assertFalse(wraith.shouldAppear(null));
        assertTrue(WatheRoles.SPECIAL_ROLES.contains(wraith));
        assertTrue(WatheRoles.ROLES.contains(wraith));
    }

    @Test
    void registersPromotionIdentitiesWithOnlyTheirApprovedFactionFlags() {
        assertEquals(Identifier.of("sparkwitch", "wind_spirit"), SparkWitchRoles.windSpirit().identifier());
        assertEquals(Identifier.of("sparkwitch", "guardian_angel"), SparkWitchRoles.guardianAngel().identifier());
        assertEquals(Identifier.of("sparkwitch", "vendetta"), SparkWitchRoles.vendetta().identifier());
        assertEquals(Identifier.of("sparkwitch", "saboteur"), SparkWitchRoles.saboteur().identifier());
        assertEquals(Identifier.of("sparkwitch", "curser"), SparkWitchRoles.curser().identifier());

        List<Role> goodRoles = List.of(
                SparkWitchRoles.windSpirit(),
                SparkWitchRoles.guardianAngel(),
                SparkWitchRoles.vendetta()
        );
        for (Role role : goodRoles) {
            assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(role));
            assertEquals(Faction.CIVILIAN, role.getFaction());
            assertEquals(0x36E51B, role.color());
            assertEquals(Role.MoodType.NONE, role.getMoodType());
            assertEquals(GameConstants.getInTicks(0, 10), role.getMaxSprintTime());
            assertFalse(role.canSeeTime());
            assertFalse(role.shouldAppear(null));
        }

        Role saboteur = SparkWitchRoles.saboteur();
        assertEquals(FactionIds.KILLER, SparkFactionApi.resolveBaseFaction(saboteur));
        assertEquals(Faction.KILLER, saboteur.getFaction());
        assertEquals(0xC13838, saboteur.color());
        assertEquals(Role.MoodType.NONE, saboteur.getMoodType());
        assertEquals(-1, saboteur.getMaxSprintTime());
        assertTrue(saboteur.canSeeTime());
        assertFalse(saboteur.shouldAppear(null));

        Role curser = SparkWitchRoles.curser();
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(curser));
        assertEquals(Faction.NEUTRAL, curser.getFaction());
        assertEquals(0xC13838, curser.color());
        assertEquals(Role.MoodType.NONE, curser.getMoodType());
        assertEquals(-1, curser.getMaxSprintTime());
        assertTrue(curser.canSeeTime());
        assertFalse(curser.shouldAppear(null));
    }

    @Test
    void exposesOnlyPromotionIdentitiesAtTheAssassinTail() {
        List<Role> guesses = SparkWitchRoles.assassinGuessRoles();
        List<Role> expectedTail = List.of(
                SparkWitchRoles.windSpirit(),
                SparkWitchRoles.guardianAngel(),
                SparkWitchRoles.vendetta(),
                SparkWitchRoles.saboteur(),
                SparkWitchRoles.curser()
        );

        assertEquals(expectedTail, guesses.subList(guesses.size() - expectedTail.size(), guesses.size()));
        assertFalse(guesses.contains(SparkWitchRoles.wraith()));
    }

    @Test
    void exposesExactCanonicalRoleAndGoalLocalizations() throws IOException {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");

        assertLocalization(english, "wraith", "Wraith",
                "Complete three tasks to earn a new identity.",
                "Complete three tasks to earn a new identity.");
        assertLocalization(english, "wind_spirit", "Wind Spirit",
                "Stay safe and survive until the end of the journey.",
                "Stay safe and survive until the end of the journey.");
        assertLocalization(english, "guardian_angel", "Guardian Angel",
                "Stay safe and survive until the end of the journey.",
                "Stay safe and survive until the end of the journey.");
        assertLocalization(english, "vendetta", "Vendetta",
                "Stay safe and survive until the end of the journey.",
                "Stay safe and survive until the end of the journey.");
        assertLocalization(english, "saboteur", "Saboteur",
                "Eliminate a passenger to succeed, before time runs out.",
                "Eliminate all civilians before time runs out.");
        assertLocalization(english, "curser", "Curser",
                "Eliminate a passenger to succeed, before time runs out.",
                "Eliminate all civilians before time runs out.");

        assertLocalization(chinese, "wraith", "冤魂",
                "完成三项任务，以获得新的身份。",
                "完成三项任务，以获得新的身份。");
        assertLocalization(chinese, "wind_spirit", "风精灵",
                "注意安全，坚持到旅程结束。",
                "注意安全，坚持到旅程结束。");
        assertLocalization(chinese, "guardian_angel", "守护天使",
                "注意安全，坚持到旅程结束。",
                "注意安全，坚持到旅程结束。");
        assertLocalization(chinese, "vendetta", "仇杀客",
                "注意安全，坚持到旅程结束。",
                "注意安全，坚持到旅程结束。");
        assertLocalization(chinese, "saboteur", "破坏者",
                "在时间耗尽前击杀一名乘客以取得胜利。",
                "在时间耗尽前消灭所有平民。");
        assertLocalization(chinese, "curser", "诅咒者",
                "在时间耗尽前击杀一名乘客以取得胜利。",
                "在时间耗尽前消灭所有平民。");
    }

    private static JsonObject language(String locale) throws IOException {
        Path path = Path.of("src/main/resources/assets/sparkwitch/lang", locale + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertLocalization(
            JsonObject language,
            String roleId,
            String name,
            String singularGoal,
            String pluralGoal
    ) {
        assertEquals(name, language.get("announcement.role.sparkwitch." + roleId).getAsString());
        assertEquals(singularGoal, language.get("announcement.goal.sparkwitch." + roleId).getAsString());
        assertEquals(name, language.get("announcement.role." + roleId).getAsString());
        assertEquals(singularGoal, language.get("announcement.goal." + roleId).getAsString());
        assertEquals(pluralGoal, language.get("announcement.goals." + roleId).getAsString());
    }
}
