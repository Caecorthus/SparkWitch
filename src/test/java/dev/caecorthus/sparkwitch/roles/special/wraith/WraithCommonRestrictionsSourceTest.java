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
    void activeWraithChatUsesWatheFinalPolicyWithoutChangingDeathState() throws Exception {
        String initializer = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/SparkWitch.java"));
        String clientInitializer = Files.readString(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java"));
        String chat = Files.readString(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithChatHudMixin.java"));
        String jump = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithJumpRestrictionMixin.java"));
        String jumpKey = Files.readString(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithJumpKeyBindingMixin.java"));
        String particles = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithGroundParticleMixin.java"));
        String landing = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithLandingParticleMixin.java"));

        assertTrue(initializer.contains("ServerMessageEvents.ALLOW_CHAT_MESSAGE.register"));
        assertTrue(initializer.contains("ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register"));
        assertTrue(initializer.contains("source.getPlayer()"));
        assertTrue(initializer.contains("WraithStateService.isActive(sender)"));
        assertTrue(initializer.contains("GameWorldComponent.KEY.get(sender.getWorld()).getRole(sender)"));
        assertTrue(initializer.contains("GuardianAngelRules.isGuardianAngel(role)"));
        assertTrue(initializer.contains("sender.isCreative()"));
        assertTrue(initializer.contains("WraithParticipationRules.mayUseTextChat("));
        assertTrue(clientInitializer.contains("AllowPlayerChat.EVENT.register"));
        assertTrue(clientInitializer.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(clientInitializer.contains("WraithParticipationRules.mayUseTextChat("));
        assertTrue(clientInitializer.contains("WraithClientState.isActive(player)"));
        assertTrue(clientInitializer.contains("GameWorldComponent.KEY.get(player.getWorld()).getRole(player)"));
        assertTrue(clientInitializer.contains("GuardianAngelRules.isGuardianAngel(role)"));
        assertTrue(clientInitializer.contains("player.isCreative()"));
        assertFalse(clientInitializer.contains("player.isSpectator()"));
        assertTrue(chat.contains("@Mixin(value = WatheClient.class, remap = false)"));
        assertTrue(chat.contains("method = \"shouldDisableChat()Z\""));
        assertTrue(chat.contains("@At(\"HEAD\")"));
        assertTrue(chat.contains("cancellable = true"));
        assertTrue(chat.contains("WraithClientState.isActive(player)"));
        assertTrue(chat.contains("WraithParticipationRules.mayUseTextChat("));
        assertTrue(chat.contains("cir.setReturnValue(!"));
        assertFalse(chat.contains("setDead("));
        assertFalse(chat.contains("setHealth("));
        assertFalse(chat.contains("requestRespawn("));
        assertFalse(chat.contains("setGameMode("));
        assertFalse(Files.exists(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithChatRestrictionMixin.java")));
        assertFalse(Files.exists(ROOT.resolve(
                "client/java/dev/caecorthus/sparkwitch/client/mixin/WraithChatScreenMixin.java")));
        assertTrue(clientInitializer.contains("ShouldAllowSuppressedKey.EVENT.register"));
        assertTrue(clientInitializer.contains("keyBinding != client.options.jumpKey"));
        assertTrue(clientInitializer.contains("WraithClientState.isActive(client.player)"));
        assertTrue(clientInitializer.contains("activeWraith && WraithParticipationRules.mayJump(true, false)"));
        assertFalse(jump.contains("method = \"wathe$restrictJump\""));
        assertTrue(jump.contains("priority = 1200"));
        assertTrue(jump.contains("sparkwitch$jumpAsWraith(player)"));
        assertTrue(jump.contains("ci.cancel()"));
        assertTrue(jump.contains("WRAITH_JUMP_REENABLE_MODIFIER"));
        assertTrue(jump.contains("Wathe.id(\"disable_jump_modifier\")"));
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
        assertTrue(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithChatHudMixin")));
        assertFalse(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithChatRestrictionMixin")));
        assertFalse(client.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithChatScreenMixin")));
    }
}
