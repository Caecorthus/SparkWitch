package dev.caecorthus.sparkwitch.client.curser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurserClientPresentationSourceTest {
    @Test
    void confusionIsPrivateAndDoesNotMutateWathePsychoState() throws Exception {
        String component = Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/witch/curser/CurserPlayerComponent.java"));
        String skin = Files.readString(Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/CurserConfusionSkinMixin.java"));
        String instinct = Files.readString(Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/CurserInstinctGateMixin.java"));
        assertTrue(component.contains("return recipient == player"));
        assertTrue(component.contains("ConfusionTicks"));
        assertTrue(skin.contains("WATHE_PSYCHO_TEXTURE"));
        assertTrue(skin.contains("target == MinecraftClient.getInstance().player"));
        assertTrue(!skin.contains("PlayerPsychoComponent"));
        assertTrue(instinct.contains("isInstinctEnabled"));
        assertTrue(instinct.contains("isLocallyConfused"));
    }

    @Test
    void playerOutlinePolicyReceivesOnlyTheConfirmedServerBooleanFromItsRegistrationBoundary() throws Exception {
        String feature = source("src/main/java/dev/caecorthus/sparkwitch/roles/witch/WitchFactionFeatureService.java");

        assertTrue(feature.contains(
                "SparkFactionApi.registerInstinctPolicy((viewer, target, gameComponent) -> "
                        + "WitchInstinctPolicy.instinctHighlight(viewer, target, gameComponent, "
                        + "SparkWitchServerConnection.isConfirmedServer()));"));
    }

    @Test
    void hudUsesTheExactActivePromotedCurserGateWithoutWatheAliveChecks() throws Exception {
        String mixin = source("src/client/java/dev/caecorthus/sparkwitch/client/mixin/CurserHudMixin.java");
        String renderer = source("src/client/java/dev/caecorthus/sparkwitch/client/curser/CurserHudRenderer.java");

        assertTrue(mixin.contains("MinecraftClient.getInstance().player"));
        assertTrue(mixin.contains("CurserHudRules.shouldRender("));
        assertTrue(mixin.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(mixin.contains("WraithClientState.isActive(player)"));
        assertTrue(mixin.contains("WraithClientState.isPromoted(player)"));
        assertTrue(mixin.contains("SparkWitchRoles.curser()"));
        assertFalse(renderer.contains("GameFunctions"));
        assertFalse(renderer.contains("isPlayerPlayingAndAlive"));
    }

    @Test
    void promotedCurserLightUsesSyncedClientStateAndWatheInstinctVetoes() throws Exception {
        String hooks = source("src/client/java/dev/caecorthus/sparkwitch/client/hooks/WitchInstinctClientHooks.java");

        assertTrue(hooks.contains("CurserInstinctClientRules.shouldUseWitchInstinctLight("));
        assertTrue(hooks.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(hooks.contains("WraithClientState.isActive(player)"));
        assertTrue(hooks.contains("WraithClientState.isPromoted(player)"));
        assertTrue(hooks.contains("GameFunctions.isPlayerSpectatingOrCreative(player)"));
        assertTrue(hooks.contains("WatheClient.isInstinctEnabled()"));
    }

    @Test
    void emptyAbilityPacketKeepsItsPayloadCodecAndServerReceiverWiring() throws Exception {
        String payload = source("src/main/java/dev/caecorthus/sparkwitch/roles/witch/curser/UseCurserAbilityC2SPacket.java");
        String packets = source("src/main/java/dev/caecorthus/sparkwitch/net/SparkWitchPackets.java");
        String client = source("src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");

        assertTrue(payload.contains("SparkWitch.id(\"use_curser_ability\")"));
        assertTrue(payload.contains("new CustomPayload.Id<>(PAYLOAD_ID)"));
        assertTrue(payload.contains("PacketCodec.of(UseCurserAbilityC2SPacket::write, UseCurserAbilityC2SPacket::read)"));
        assertTrue(packets.contains("PayloadTypeRegistry.playC2S().register(UseCurserAbilityC2SPacket.ID, UseCurserAbilityC2SPacket.CODEC)"));
        assertTrue(packets.contains("ServerPlayNetworking.registerGlobalReceiver(UseCurserAbilityC2SPacket.ID,"));
        assertTrue(packets.contains("CurserFeatureService.use(context.player())"));
        assertTrue(client.contains("ClientPlayNetworking.canSend(UseCurserAbilityC2SPacket.ID)"));
        assertTrue(client.contains("CurserClientHooks.use()"));
    }

    @Test
    void emptyTargetSelectionReturnsBeforeCooldownStarts() throws Exception {
        String feature = source("src/main/java/dev/caecorthus/sparkwitch/roles/witch/curser/CurserFeatureService.java");

        int emptyTargetGate = feature.indexOf("targets.isEmpty()");
        int cooldownStart = feature.indexOf("state.startCooldown()");
        assertTrue(emptyTargetGate >= 0);
        assertTrue(cooldownStart > emptyTargetGate);
        assertTrue(feature.substring(emptyTargetGate, cooldownStart).contains("||"));
        assertTrue(feature.substring(emptyTargetGate, feature.indexOf("for (ServerPlayerEntity target", cooldownStart))
                .contains("return;"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path)).replaceAll("\\s+", " ");
    }
}
