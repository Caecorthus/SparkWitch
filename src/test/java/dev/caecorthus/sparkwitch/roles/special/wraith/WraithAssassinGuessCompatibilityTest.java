package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithAssassinGuessCompatibilityTest {
    private static final Path MIXIN_SOURCE = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/AssassinSaboteurGuessMixin.java");
    private static final Path MIXIN_CONFIG = Path.of(
            "src/client/resources/sparkwitch.client.mixins.json");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void bypassesTheNativeKillerFilterForSaboteurOnly() {
        assertTrue(SparkWitchRoles.saboteur().canUseKiller());
        assertFalse(WraithPromotionRoles.shouldExcludeFromAssassinGuess(SparkWitchRoles.saboteur()));

        assertTrue(SparkWitchRoles.ninja().canUseKiller());
        assertTrue(WraithPromotionRoles.shouldExcludeFromAssassinGuess(SparkWitchRoles.ninja()));
        assertFalse(WraithPromotionRoles.shouldExcludeFromAssassinGuess(SparkWitchRoles.windSpirit()));
    }

    @Test
    void redirectsOnlyNoellesNativeKillerFilterAndRegistersTheClientMixin() throws IOException {
        String source = Files.readString(MIXIN_SOURCE);
        assertTrue(source.contains("@Mixin(AssassinScreen.class)"));
        assertTrue(source.contains("method = \"getAllGuessableRoles\""));
        assertTrue(source.contains("target = \"Ldev/doctor4t/wathe/api/Role;canUseKiller()Z\""));
        assertTrue(source.contains("WraithPromotionRoles.shouldExcludeFromAssassinGuess(role)"));

        JsonObject config = JsonParser.parseString(Files.readString(MIXIN_CONFIG)).getAsJsonObject();
        JsonArray clientMixins = config.getAsJsonArray("client");
        assertTrue(clientMixins.contains(JsonParser.parseString("\"AssassinSaboteurGuessMixin\"")));
    }
}
