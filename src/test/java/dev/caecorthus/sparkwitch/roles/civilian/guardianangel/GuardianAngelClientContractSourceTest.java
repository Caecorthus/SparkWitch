package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianAngelClientContractSourceTest {
    private static final Path CLIENT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void sharedPrimaryKeySendsOnlyTheGuardianOwnedEmptyRequest() throws IOException {
        String client = read(CLIENT.resolve("SparkWitchClient.java"));

        assertTrue(client.contains("WitchAbilityKeyBridge.wasPressed()"));
        assertTrue(client.contains("GuardianAngelRules.isGuardianAngel(role)"));
        assertTrue(client.contains("ClientPlayNetworking.send(new UseGuardianAngelSkillC2SPacket())"));
        assertFalse(client.contains("SecondaryAbilityRegistry"));
    }

    @Test
    void privateHudPreviewsOnlyAValidThreeBlockTarget() throws IOException {
        String targeting = read(CLIENT.resolve("guardianangel/GuardianAngelTargetingPreview.java"));
        String hud = read(CLIENT.resolve("guardianangel/GuardianAngelHudRenderer.java"));

        assertTrue(targeting.contains("MinecraftClient.getInstance().crosshairTarget"));
        assertTrue(targeting.contains("GuardianAngelRules.canTarget("));
        assertTrue(targeting.contains("GuardianAngelEffects.guardianShield()"));
        assertTrue(hud.contains("GuardianAngelPlayerComponent.KEY.get(player)"));
        assertTrue(hud.contains("hud.sparkwitch.guardian_angel.cooldown"));
        assertTrue(hud.contains("hud.sparkwitch.guardian_angel.no_target"));
        assertTrue(hud.contains("hud.sparkwitch.guardian_angel.ready"));
        assertTrue(hud.contains("SparkWitchClient.abilityKeyText()"));
    }

    @Test
    void ownerPrivateShieldOutlineWinsBeforeWraithPrivacy() throws IOException {
        String hooks = read(CLIENT.resolve("guardianangel/GuardianAngelClientHooks.java"));
        String wraith = read(CLIENT.resolve("mixin/WraithWatheHighlightMixin.java"));
        String poisonRules = read(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/compat/WitchPoisonVisionRules.java"));

        assertTrue(hooks.contains("GuardianAngelPlayerComponent.KEY.get(viewer)"));
        assertTrue(hooks.contains("component.getShieldTargetUuid()"));
        assertTrue(hooks.contains("GuardianAngelRules.COLOR"));
        assertTrue(poisonRules.contains("SparkWitchRoles.guardianAngel()"));

        int guardian = wraith.indexOf("GuardianAngelClientHooks.shieldTargetHighlight");
        int spectator = wraith.indexOf("WraithViewerRules.shouldRevealToSpectator");
        int hidden = wraith.indexOf("WraithViewerRules.shouldHideFromOrdinaryViewer");
        assertTrue(guardian >= 0 && guardian < spectator && guardian < hidden);
        assertTrue(wraith.contains("@Mixin(value = WatheClient.class, remap = false, priority = 2000)"));
    }

    @Test
    void clientMixinConfigOwnsTheGuardianHudOnly() throws IOException {
        String mixins = read(Path.of("src/client/resources/sparkwitch.client.mixins.json"));

        assertTrue(mixins.contains("GuardianAngelHudMixin"));
        assertFalse(mixins.contains("GuardianAngelSecondaryAbility"));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "required source must exist: " + path);
        return Files.readString(path);
    }
}
