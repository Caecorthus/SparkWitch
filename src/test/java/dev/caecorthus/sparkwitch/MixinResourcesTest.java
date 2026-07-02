package dev.caecorthus.sparkwitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinResourcesTest {
    private static final Path SERVER_MIXINS = Path.of("src/main/resources/sparkwitch.mixins.json");
    private static final Path CLIENT_MIXINS = Path.of("src/client/resources/sparkwitch.client.mixins.json");

    @Test
    void serverMixinsRegisterFirePokerFallAttribution() throws IOException {
        JsonObject mixins = readJson(SERVER_MIXINS);

        assertEquals("dev.caecorthus.sparkwitch.mixin", mixins.get("package").getAsString());
        assertTrue(contains(mixins.getAsJsonArray("mixins"), "MurderGameModeMixin"));
        assertTrue(contains(mixins.getAsJsonArray("mixins"), "GameWorldComponentMixin"));
    }

    @Test
    void serverMixinsRegisterPlayerBodyEquipmentCompatibility() throws IOException {
        JsonObject mixins = readJson(SERVER_MIXINS);

        assertTrue(contains(mixins.getAsJsonArray("mixins"), "PlayerBodyEntityEquipmentMixin"));
    }

    @Test
    void clientMixinsDoNotRegisterServerFallAttribution() throws IOException {
        JsonObject mixins = readJson(CLIENT_MIXINS);

        assertFalse(contains(mixins.getAsJsonArray("client"), "GameWorldComponentMixin"));
    }

    @Test
    void clientMixinsRegisterWitchSkillInventoryPanel() throws IOException {
        JsonObject mixins = readJson(CLIENT_MIXINS);

        assertTrue(contains(mixins.getAsJsonArray("client"), "WitchSkillInventoryScreenMixin"));
    }

    @Test
    void clientMixinsRegisterDeathRayAttackHook() throws IOException {
        JsonObject mixins = readJson(CLIENT_MIXINS);

        assertTrue(contains(mixins.getAsJsonArray("client"), "DeathRayAttackMixin"));
        assertTrue(contains(mixins.getAsJsonArray("client"), "DeathRayBlockBreakingMixin"));
    }

    @Test
    void clientMixinsRegisterRoleNameDisplayFallback() throws IOException {
        JsonObject mixins = readJson(CLIENT_MIXINS);

        assertTrue(contains(mixins.getAsJsonArray("client"), "WitchRoleNameDisplayMixin"));
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static boolean contains(JsonArray array, String value) {
        return StreamSupport.stream(array.spliterator(), false)
                .anyMatch(element -> value.equals(element.getAsString()));
    }
}
