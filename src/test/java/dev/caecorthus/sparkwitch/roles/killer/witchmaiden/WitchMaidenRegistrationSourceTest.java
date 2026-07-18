package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchMaidenRegistrationSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final Path LANG = Path.of("src/main/resources/assets/sparkwitch/lang");

    @Test
    void registersWitchMaidenAsOneAlwaysEligibleKillerCandidate() throws IOException {
        String rules = read("roles/killer/witchmaiden/WitchMaidenRules.java");
        String registry = read("registry/SparkWitchRoleRegistry.java");
        String facade = read("SparkWitchRoles.java");

        assertTrue(rules.contains("ROLE_ID = SparkWitch.id(\"witch_maiden\")"));
        assertTrue(rules.contains("COLOR = 0xB04A8B"));

        int start = registry.indexOf("witchMaiden = SparkFactionApi.registerRole");
        int end = registry.indexOf(".build());", start);
        assertTrue(start >= 0 && end > start);
        String registration = registry.substring(start, end + ".build());".length());

        assertTrue(registration.contains("builder(WITCH_MAIDEN_ID, FactionIds.KILLER)"));
        assertTrue(registration.contains(".color(WitchMaidenRules.COLOR)"));
        assertTrue(registration.contains(".moodType(Role.MoodType.FAKE)"));
        assertTrue(registration.contains(".maxSprintTime(-1)"));
        assertTrue(registration.contains(".canSeeTime(true)"));
        assertTrue(registration.contains(".nativeWatheFaction(Faction.KILLER)"));
        assertFalse(registration.contains(".appearanceCondition("));

        int guessMethod = registry.indexOf("private static List<Role> assassinGuessRolesInOrder()");
        int listStart = registry.indexOf("return List.of(", guessMethod);
        int listEnd = registry.indexOf(");", listStart);
        String guessOrder = registry.substring(listStart, listEnd);
        assertTrue(guessOrder.indexOf("blackRaven") < guessOrder.indexOf("witchMaiden"));
        assertTrue(guessOrder.indexOf("witchMaiden") < guessOrder.indexOf("hunter"));
        assertTrue(registry.contains("|| role == witchMaiden"));

        assertTrue(facade.contains(
                "WITCH_MAIDEN_ID = SparkWitchRoleRegistry.WITCH_MAIDEN_ID"));
        assertTrue(facade.contains("public static Role witchMaiden()"));
    }

    @Test
    void localizesTheRegisteredRoleInBothLocales() throws IOException {
        JsonObject chinese = parse("zh_cn");
        JsonObject english = parse("en_us");

        assertEquals("巫女", chinese.get("announcement.role.witch_maiden").getAsString());
        assertEquals("Witch Maiden", english.get("announcement.role.witch_maiden").getAsString());
        assertEquals("杀手胜利", chinese.get("announcement.win.witch_maiden").getAsString());
        assertEquals("Killer Victory", english.get("announcement.win.witch_maiden").getAsString());
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath));
    }

    private static JsonObject parse(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json")))
                .getAsJsonObject();
    }
}
