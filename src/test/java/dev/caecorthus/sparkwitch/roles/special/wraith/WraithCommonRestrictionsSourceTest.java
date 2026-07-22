package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithCommonRestrictionsSourceTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void chatRemainsNativeWhileJumpAndParticlesDelegateToCentralPolicy() throws Exception {
        String initializer = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/SparkWitch.java"));
        String clientInitializer = Files.readString(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java"));
        String jump = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithJumpRestrictionMixin.java"));
        String jumpKey = Files.readString(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithJumpKeyBindingMixin.java"));
        String particles = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithGroundParticleMixin.java"));
        String landing = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithLandingParticleMixin.java"));

        assertFalse(initializer.contains("ServerMessageEvents.ALLOW_CHAT_MESSAGE"));
        assertTrue(clientInitializer.contains("AllowPlayerChat.EVENT.register"));
        assertTrue(clientInitializer.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(clientInitializer.contains("WraithParticipationRules.mayUseTextChat("));
        assertTrue(clientInitializer.contains("WraithClientState.isActive(player)"));
        assertFalse(Files.exists(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithChatRestrictionMixin.java")));
        assertFalse(Files.exists(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithChatScreenMixin.java")));
        assertTrue(jump.contains("getJumpConfig().allowed()"));
        assertTrue(jump.contains("method = \"jump\""));
        assertTrue(jumpKey.contains("options.jumpKey"));
        assertTrue(jumpKey.contains("getJumpConfig().allowed()"));
        assertTrue(particles.contains("method = \"shouldSpawnSprintingParticles\""));
        assertTrue(particles.contains("method = \"spawnSprintingParticles\""));
        assertTrue(landing.contains("method = \"fall\""));
        assertTrue(landing.contains("ServerWorld;spawnParticles"));
        for (String source : new String[]{jump, jumpKey, particles, landing}) {
            assertTrue(source.contains("WraithParticipationRules."));
            assertTrue(source.contains("WraithStateService.isActive"));
        }
    }

    @Test
    void onlyJumpAndParticleMixinsAreRequiredAndRegistered() throws Exception {
        JsonObject common = JsonParser.parseString(Files.readString(ROOT.resolve(
                "main/resources/sparkwitch.mixins.json"))).getAsJsonObject();
        JsonObject client = JsonParser.parseString(Files.readString(ROOT.resolve(
                "client/resources/sparkwitch.client.mixins.json"))).getAsJsonObject();
        assertTrue(common.get("required").getAsBoolean());
        assertTrue(client.get("required").getAsBoolean());
        assertTrue(common.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithJumpRestrictionMixin")));
        assertTrue(common.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithGroundParticleMixin")));
        assertTrue(common.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithLandingParticleMixin")));
        assertTrue(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithJumpKeyBindingMixin")));
        assertFalse(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithChatRestrictionMixin")));
        assertFalse(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithChatScreenMixin")));
    }
}
