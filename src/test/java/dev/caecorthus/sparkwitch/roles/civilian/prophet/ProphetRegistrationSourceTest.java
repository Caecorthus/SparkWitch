package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRegistrationSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java");
    private static final Path FACADE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/SparkWitchRoles.java");
    private static final Path LANG = Path.of("src/main/resources/assets/sparkwitch/lang");

    @Test
    void registersProphetAsAnAlwaysEligibleCivilianRole() throws IOException {
        String registry = Files.readString(REGISTRY);
        assertTrue(registry.contains(
                "FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)"));
        assertTrue(registry.contains(".color(ProphetRules.ROLE_COLOR)"));
        assertTrue(registry.contains(".moodType(Role.MoodType.REAL)"));
        assertTrue(registry.contains(".maxSprintTime(GameConstants.getInTicks(0, 10))"));
        assertTrue(registry.contains(".canSeeTime(false)"));
        assertTrue(registry.contains(".nativeWatheFaction(Faction.CIVILIAN)"));
        assertTrue(registry.contains("|| role == prophet"));

        int registrationStart = registry.indexOf(
                "FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)");
        int registrationEnd = registry.indexOf(".build());", registrationStart);
        assertTrue(registrationStart >= 0 && registrationEnd > registrationStart);
        assertFalse(registry.substring(registrationStart, registrationEnd)
                .contains(".appearanceCondition("));

        int method = registry.indexOf("private static List<Role> assassinGuessRolesInOrder()");
        int listStart = registry.indexOf("return List.of(", method);
        int listEnd = registry.indexOf(");", listStart);
        String guessOrder = registry.substring(listStart, listEnd);
        assertTrue(guessOrder.contains("prophet"));
        assertTrue(guessOrder.indexOf("apprenticeWitch") < guessOrder.indexOf("prophet"));
        assertTrue(guessOrder.indexOf("prophet") < guessOrder.indexOf("murderousWitch"));
        assertTrue(Files.readString(FACADE).contains("public static Role prophet()"));
    }

    @Test
    void localizesTheApprovedRoleCopy() throws IOException {
        JsonObject chinese = parse("zh_cn");
        JsonObject english = parse("en_us");
        assertEquals("先知", chinese.get("announcement.role.prophet").getAsString());
        assertEquals("帮助好人阵营，预见死亡留下的痕迹。",
                chinese.get("announcement.goal.prophet").getAsString());
        assertEquals("Prophet", english.get("announcement.role.prophet").getAsString());
    }

    private static JsonObject parse(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json")))
                .getAsJsonObject();
    }
}
