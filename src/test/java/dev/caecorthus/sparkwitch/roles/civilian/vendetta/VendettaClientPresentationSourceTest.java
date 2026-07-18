package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaClientPresentationSourceTest {
    @Test
    void exactBoundPairOwnsUnconditionalHighlightsAndWraithPrivacyExceptions() throws IOException {
        String presentation = readClient("vendetta/VendettaClientPresentation.java");
        String highlight = readClient("mixin/WraithWatheHighlightMixin.java");
        String blackRavenResolver = readClient("blackraven/BlackRavenInstinctClientHooks.java");
        String privacy = readClient("render/WraithViewerRules.java");
        String invisibility = readClient("mixin/WraithEntityInvisibilityMixin.java");

        assertTrue(presentation.contains("getBoundKillerUuid()"));
        assertTrue(presentation.contains("isBoundViewer()"));
        assertTrue(presentation.contains("viewer == MinecraftClient.getInstance().player"));
        assertTrue(presentation.contains("VendettaPlayerComponent.KEY.get(player).isActive()"));
        assertTrue(presentation.contains("Math.sqrt(viewer.squaredDistanceTo(killer))"));
        assertTrue(presentation.contains("VendettaRole.ROLE_ID.equals(role.identifier())"));
        assertTrue(highlight.contains("VendettaClientPresentation.highlight(viewer, playerTarget)"));
        assertTrue(highlight.indexOf("VendettaClientPresentation.highlight")
                < highlight.indexOf("WraithViewerRules.shouldRevealToSpectator"));
        assertTrue(blackRavenResolver.contains("VendettaClientPresentation.highlight(viewer, targetPlayer)"));
        assertTrue(privacy.contains("!VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target)"));
        assertTrue(invisibility.contains("VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target)"));
    }

    @Test
    void ownerVisionHudAndSpectatorProjectionUseExistingClientPipelines() throws IOException {
        String effects = readClient("blackraven/BlackRavenPerceptionScreenEffects.java");
        String hud = readClient("vendetta/VendettaHudRenderer.java");
        String projection = readClient("render/WraithSteveProjection.java");
        String heldItems = readClient("mixin/WraithHeldItemFeatureRendererMixin.java");
        String mixins = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));

        assertTrue(effects.contains("VendettaClientPresentation.hasActiveOwnerState(player)"));
        assertTrue(effects.contains("VendettaClientPresentation.desaturation(player)"));
        assertTrue(hud.contains("hud.sparkwitch.vendetta.reveal_countdown"));
        assertTrue(hud.contains("hud.sparkwitch.vendetta.reveal_remaining"));
        assertTrue(projection.contains("VendettaClientPresentation.shouldProjectSpectatorSteve(viewer, target)"));
        assertTrue(heldItems.contains("VendettaClientPresentation.shouldProjectSpectatorSteve(viewer, player)"));
        assertTrue(mixins.contains("\"VendettaHudMixin\""));
        assertTrue(mixins.contains("\"VendettaPlayerLabelMixin\""));
    }

    @Test
    void boundKillerKeepsRealSkinButLosesVendettaNameAndCape() throws IOException {
        String skinMetadata = readClient("mixin/blackraven/BlackRavenSkinTexturesMixin.java");
        String skinTexture = readClient("mixin/blackraven/BlackRavenPlayerTextureMixin.java");
        String roleName = readClient("mixin/WraithNameMixin.java");
        String worldLabel = readClient("mixin/VendettaPlayerLabelMixin.java");
        String cape = readClient("mixin/WraithCapeFeatureRendererMixin.java");

        assertTrue(skinMetadata.contains("VendettaClientPresentation.isBoundKillerViewingVendetta(client.player, player)"));
        assertTrue(skinTexture.contains("!VendettaClientPresentation.isBoundKillerViewingVendetta(client.player, player)"));
        assertTrue(roleName.contains("VendettaClientPresentation.isBoundKillerViewingVendetta"));
        assertTrue(worldLabel.contains("cir.setReturnValue(false)"));
        assertTrue(cape.contains("VendettaClientPresentation.isBoundKillerViewingVendetta"));
    }

    @Test
    void hudTranslationsMatchTheRequestedChineseCopy() throws IOException {
        JsonObject english = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/sparkwitch/lang/en_us.json")
        )).getAsJsonObject();
        JsonObject chinese = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json")
        )).getAsJsonObject();

        assertEquals("Reveal countdown: %s s", english.get("hud.sparkwitch.vendetta.reveal_countdown").getAsString());
        assertEquals("Reveal remaining: %s s", english.get("hud.sparkwitch.vendetta.reveal_remaining").getAsString());
        assertEquals("透视倒计时：%s 秒", chinese.get("hud.sparkwitch.vendetta.reveal_countdown").getAsString());
        assertEquals("透视剩余时间：%s 秒", chinese.get("hud.sparkwitch.vendetta.reveal_remaining").getAsString());
    }

    private static String readClient(String relativePath) throws IOException {
        Path path = Path.of("src/client/java/dev/caecorthus/sparkwitch/client").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
