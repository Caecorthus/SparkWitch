package dev.caecorthus.sparkwitch.client.saboteur;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurHudSourceTest {
    private static final Path CLIENT_ROOT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void hudUsesDedicatedSyncedCooldownAndOnlyTheSpecifiedCopy() throws IOException {
        String renderer = read(CLIENT_ROOT.resolve("saboteur/SaboteurHudRenderer.java"));

        assertTrue(renderer.contains("SaboteurPlayerComponent.KEY.maybeGet(player)"));
        assertTrue(renderer.contains("getCooldownTicks()"));
        assertTrue(renderer.contains("hud.sparkwitch.skill.sabotage.cooldown"));
        assertTrue(renderer.contains("hud.sparkwitch.skill.sabotage.ready"));
        assertTrue(renderer.contains("SaboteurHudRules.cooldownSeconds(cooldownTicks)"));
        assertTrue(renderer.contains("WitchAbilityKeyBridge.keyText()"));
        assertFalse(renderer.contains("WitchPlayerComponent"));
        assertFalse(renderer.contains("Text.literal"));
    }

    @Test
    void mixinFailsClosedToTheExactActivePromotedSaboteur() throws IOException {
        String mixin = read(CLIENT_ROOT.resolve("mixin/saboteur/SaboteurHudMixin.java"));

        assertTrue(mixin.contains("SaboteurRole.ID.equals(role.identifier())"));
        assertTrue(mixin.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(mixin.contains("WraithClientState.isPromoted(player)"));
        assertFalse(mixin.contains("GameFunctions.isPlayerPlayingAndAlive"));
        assertFalse(mixin.contains("WitchPlayerComponent"));
    }

    @Test
    void actualNoellesAbilityBindingDispatchesTheDedicatedPacketFirst() throws IOException {
        String client = read(CLIENT_ROOT.resolve("SparkWitchClient.java"));
        String bridge = read(CLIENT_ROOT.resolve("hooks/WitchAbilityKeyBridge.java"));

        assertTrue(client.contains("WitchAbilityKeyBridge.wasPressed()"));
        assertTrue(client.contains("SaboteurRole.ID.equals(role.identifier())"));
        assertTrue(client.contains("WraithClientState.isPromoted(client.player)"));
        assertTrue(client.contains("ClientPlayNetworking.send(new UseSaboteurSkillC2SPacket())"));
        assertTrue(client.indexOf("new UseSaboteurSkillC2SPacket()")
                < client.indexOf("WitchPlayerComponent.KEY.get(client.player).hasSkill()"));
        assertTrue(bridge.contains("ABILITY_FIELD = \"abilityBind\""));
        assertTrue(bridge.contains("keyBinding.getBoundKeyLocalizedText()"));
    }

    @Test
    void dedicatedHudIsRegisteredAndTheGenericWitchHudHasNoSaboteurBranch() throws IOException {
        String config = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));
        String genericHud = read(CLIENT_ROOT.resolve("hud/WitchSkillHudRenderer.java"));

        assertTrue(config.contains("\"saboteur.SaboteurHudMixin\""));
        assertFalse(genericHud.contains("Saboteur"));
    }

    @Test
    void saboteurNeverEntersTheWitchInventoryPanel() throws IOException {
        String inventory = read(CLIENT_ROOT.resolve("mixin/WitchSkillInventoryScreenMixin.java"));
        String presentation = read(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/skill/WitchSkillPresentationRules.java"
        ));

        assertFalse(inventory.contains("SaboteurRole"));
        assertFalse(presentation.contains("SaboteurRole"));
        assertTrue(presentation.contains("SparkWitchRoles.grandWitch()"));
        assertTrue(presentation.contains("SparkWitchRoles.apprenticeWitch()"));
        assertTrue(presentation.contains("SparkWitchRoles.murderousWitch()"));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "required source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
