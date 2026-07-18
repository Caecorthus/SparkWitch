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

        List<Role> goodRoles = List.of(SparkWitchRoles.windSpirit());
        for (Role role : goodRoles) {
            assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(role));
            assertEquals(Faction.CIVILIAN, role.getFaction());
            assertEquals(0x36E51B, role.color());
            assertEquals(Role.MoodType.NONE, role.getMoodType());
            assertEquals(GameConstants.getInTicks(0, 10), role.getMaxSprintTime());
            assertFalse(role.canSeeTime());
            assertFalse(role.shouldAppear(null));
        }

        Role vendetta = SparkWitchRoles.vendetta();
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(vendetta));
        assertEquals(Faction.CIVILIAN, vendetta.getFaction());
        assertEquals(0x36E51B, vendetta.color());
        assertEquals(Role.MoodType.NONE, vendetta.getMoodType());
        assertEquals(-1, vendetta.getMaxSprintTime());
        assertFalse(vendetta.canSeeTime());
        assertFalse(vendetta.shouldAppear(null));

        Role guardianAngel = SparkWitchRoles.guardianAngel();
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(guardianAngel));
        assertEquals(Faction.CIVILIAN, guardianAngel.getFaction());
        assertEquals(0x36E51B, guardianAngel.color());
        assertEquals(Role.MoodType.NONE, guardianAngel.getMoodType());
        assertEquals(-1, guardianAngel.getMaxSprintTime());
        assertFalse(guardianAngel.canSeeTime());
        assertFalse(guardianAngel.shouldAppear(null));

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
                "Complete three tasks to earn a new identity.",
                "Complete three tasks to earn a new identity.");
        assertLocalization(english, "wind_spirit", "Wind Spirit",
                "Stay safe and survive until the end of the journey.",
                english.get("announcement.goal.wind_spirit").getAsString(),
                english.get("announcement.goals.wind_spirit").getAsString());
        assertLocalization(english, "guardian_angel", "Guardian Angel",
                "Listen to the dead. Protect the living.",
                "Listen to the dead. Protect the living.",
                "Help the Civilian faction. You have unlimited stamina, are immune to blackouts, and can "
                        + "identify poisoned food, drinks, and beds; you can communicate by voice only with the dead. "
                        + "60 seconds after promotion, aim at a living player within 3 blocks and press the ability key "
                        + "to secretly apply one Guardian Shield for 10 seconds, using the same protection rules as the "
                        + "Iron Man potion; a successful cast starts a 90-second cooldown. While the shield is active, "
                        + "the target is highlighted through walls in your role color and visible only to you; the target "
                        + "does not know they are protected.");
        assertLocalization(english, "vendetta", "Vendetta",
                "Hunt the killer bound to your first death.",
                "Hunt the killer bound to your first death.",
                english.get("announcement.goals.vendetta").getAsString());
        assertLocalization(english, "saboteur", "Saboteur",
                "Eliminate a passenger to succeed, before time runs out.",
                "Eliminate the passengers through sabotage.",
                english.get("announcement.goals.saboteur").getAsString());
        assertLocalization(english, "curser", "Curser",
                "Eliminate a passenger to succeed, before time runs out.",
                "Eliminate a passenger to succeed, before time runs out.",
                "Eliminate all civilians before time runs out.");

        assertLocalization(chinese, "wraith", "冤魂",
                "完成三项任务，以获得新的身份。",
                "完成三项任务，以获得新的身份。",
                "完成三项任务，以获得新的身份。");
        assertLocalization(chinese, "wind_spirit", "风精灵",
                "注意安全，坚持到旅程结束。",
                chinese.get("announcement.goal.wind_spirit").getAsString(),
                chinese.get("announcement.goals.wind_spirit").getAsString());
        assertLocalization(chinese, "guardian_angel", "守护天使",
                "倾听亡者，守护生者。",
                "倾听亡者，守护生者。",
                "帮助好人阵营。你拥有无限体力、免疫熄灯，并能识别被下毒的食物、饮品和床；你只能与死者进行语音交流。"
                        + "晋升60秒后，瞄准三格内一名存活玩家并按下技能键，可秘密施加一层持续10秒、判定与铁人药剂相同的守护护盾；成功施放后冷却90秒。"
                        + "护盾生效期间，目标会以你的身份色隔墙高亮且仅你可见；目标不会知道自己受到保护。");
        assertLocalization(chinese, "vendetta", "仇杀客",
                "追杀首次死亡时与你绑定的凶手。",
                "追杀首次死亡时与你绑定的凶手。",
                chinese.get("announcement.goals.vendetta").getAsString());
        assertLocalization(chinese, "saboteur", "破坏者",
                "在时间耗尽前击杀一名乘客以取得胜利。",
                "通过破坏行动消灭好人阵营。",
                chinese.get("announcement.goals.saboteur").getAsString());
        assertLocalization(chinese, "curser", "诅咒者",
                "在时间耗尽前击杀一名乘客以取得胜利。",
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
            String namespacedGoal,
            String singularGoal,
            String pluralGoal
    ) {
        assertEquals(name, language.get("announcement.role.sparkwitch." + roleId).getAsString());
        assertEquals(namespacedGoal, language.get("announcement.goal.sparkwitch." + roleId).getAsString());
        assertEquals(name, language.get("announcement.role." + roleId).getAsString());
        assertEquals(singularGoal, language.get("announcement.goal." + roleId).getAsString());
        assertEquals(pluralGoal, language.get("announcement.goals." + roleId).getAsString());
    }
}
