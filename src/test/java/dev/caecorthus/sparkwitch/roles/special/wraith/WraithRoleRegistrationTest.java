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

        List<Role> goodRoles = List.of(SparkWitchRoles.windSpirit());
        for (Role role : goodRoles) {
            assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(role));
            assertEquals(Faction.CIVILIAN, role.getFaction());
            assertEquals(0x59D8E6, role.color());
            assertEquals(Role.MoodType.NONE, role.getMoodType());
            assertEquals(-1, role.getMaxSprintTime());
            assertFalse(role.canSeeTime());
            assertFalse(role.shouldAppear(null));
        }

        Role vendetta = SparkWitchRoles.vendetta();
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(vendetta));
        assertEquals(Faction.CIVILIAN, vendetta.getFaction());
        assertEquals(0xE34B5F, vendetta.color());
        assertEquals(Role.MoodType.NONE, vendetta.getMoodType());
        assertEquals(-1, vendetta.getMaxSprintTime());
        assertFalse(vendetta.canSeeTime());
        assertFalse(vendetta.shouldAppear(null));

        Role guardianAngel = SparkWitchRoles.guardianAngel();
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(guardianAngel));
        assertEquals(Faction.CIVILIAN, guardianAngel.getFaction());
        assertEquals(0xF0D77A, guardianAngel.color());
        assertEquals(Role.MoodType.NONE, guardianAngel.getMoodType());
        assertEquals(-1, guardianAngel.getMaxSprintTime());
        assertFalse(guardianAngel.canSeeTime());
        assertFalse(guardianAngel.shouldAppear(null));

        Role saboteur = SparkWitchRoles.saboteur();
        assertEquals(FactionIds.KILLER, SparkFactionApi.resolveBaseFaction(saboteur));
        assertEquals(Faction.KILLER, saboteur.getFaction());
        assertEquals(0xE28743, saboteur.color());
        assertEquals(Role.MoodType.NONE, saboteur.getMoodType());
        assertEquals(-1, saboteur.getMaxSprintTime());
        assertTrue(saboteur.canSeeTime());
        assertFalse(saboteur.shouldAppear(null));

        Role curser = SparkWitchRoles.curser();
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(curser));
        assertEquals(Faction.NEUTRAL, curser.getFaction());
        assertEquals(0xA968D5, curser.color());
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
                "Complete three tasks and earn a new identity.");
        assertLocalization(english, "wind_spirit", "Wind Spirit",
                "Stay swift, complete tasks, and survive the journey.");
        assertLocalization(english, "guardian_angel", "Guardian Angel",
                "Protect your companions.");
        assertLocalization(english, "vendetta", "Vendetta",
                "Find the killer bound to your death and take revenge.");
        assertLocalization(english, "saboteur", "Saboteur",
                "Black out the train and aid the killers.");
        assertLocalization(english, "curser", "Curser",
                "Curse the living before time runs out.");

        assertLocalization(chinese, "wraith", "冤魂",
                "完成三项任务，获得新的身份。");
        assertLocalization(chinese, "wind_spirit", "风精灵",
                "保持迅捷、完成任务并活到旅程结束。");
        assertLocalization(chinese, "guardian_angel", "守护天使",
                "守护你的同伴。");
        assertLocalization(chinese, "vendetta", "仇杀客",
                "找到与你死亡绑定的凶手并完成复仇。");
        assertLocalization(chinese, "saboteur", "破坏者",
                "熄灭列车灯光，协助杀手消灭好人阵营。");
        assertLocalization(chinese, "curser", "诅咒者",
                "诅咒附近玩家，并在时间耗尽前消灭平民。");
    }

    private static JsonObject language(String locale) throws IOException {
        Path path = Path.of("src/main/resources/assets/sparkwitch/lang", locale + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertLocalization(
            JsonObject language,
            String roleId,
            String name,
            String goal
    ) {
        assertEquals(name, language.get("announcement.role.sparkwitch." + roleId).getAsString());
        assertEquals(goal, language.get("announcement.goal.sparkwitch." + roleId).getAsString());
        assertEquals(name, language.get("announcement.role." + roleId).getAsString());
        assertEquals(goal, language.get("announcement.goal." + roleId).getAsString());
        assertEquals(goal, language.get("announcement.goals." + roleId).getAsString());
    }
}
