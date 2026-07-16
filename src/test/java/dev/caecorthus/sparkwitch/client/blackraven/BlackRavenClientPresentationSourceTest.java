package dev.caecorthus.sparkwitch.client.blackraven;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackRavenClientPresentationSourceTest {
    private static final Path CLIENT_ROOT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");
    private static final Path CLIENT_RESOURCES = Path.of("src/client/resources");

    @Test
    void secondaryKeyIsGenericRoleDispatchedAndDefaultsToN() throws IOException {
        String controller = readClient("ability/SecondaryAbilityController.java");
        String registry = readClient("ability/SecondaryAbilityRegistry.java");
        String client = readClient("SparkWitchClient.java");

        assertTrue(controller.contains("GLFW.GLFW_KEY_N"));
        assertTrue(controller.contains("KeyBindingHelper.registerKeyBinding"));
        assertTrue(controller.contains("GameWorldComponent.KEY.get"));
        assertTrue(registry.contains("Map<Identifier, SecondaryAbilityHandler>"));
        assertTrue(registry.contains("throw new IllegalStateException"));
        assertFalse(controller.contains("BlackRaven"));
        assertTrue(client.contains("SecondaryAbilityController.registerKeyBinding()"));
        assertTrue(client.contains("SecondaryAbilityController.tick(client)"));
        assertTrue(client.contains("SecondaryAbilityController.reset()"));
    }

    @Test
    void blackRavenOwnsModeResetInputLockAndSecondHudRow() throws IOException {
        String state = readClient("blackraven/BlackRavenClientState.java");
        String module = readClient("blackraven/BlackRavenClientModule.java");
        String hud = readClient("blackraven/BlackRavenHudRenderer.java");
        String hudMixin = readClient("mixin/blackraven/BlackRavenHudMixin.java");
        String sharedHud = readClient("hud/WitchSkillHudRenderer.java");

        assertTrue(state.contains("NORMAL"));
        assertTrue(state.contains("SENSED_ONLY"));
        assertTrue(state.contains("if (isPerceptionActive(player))"));
        assertTrue(state.contains("GameFunctions.isPlayerPlayingAndAlive(player)"));
        assertTrue(state.contains("gameComponent.isRunning()"));
        assertTrue(state.contains("mode = InstinctMode.NORMAL"));
        assertTrue(module.contains("SecondaryAbilityRegistry.register(BlackRavenRules.ROLE_ID"));
        assertTrue(hud.contains("secondaryKeyText()"));
        assertTrue(hud.contains("getScaledWindowHeight()"));
        assertTrue(hudMixin.contains("BlackRavenHudRenderer.render"));
        assertFalse(sharedHud.contains("BlackRaven"));
    }

    @Test
    void perceptionEffectUsesExactHalfDesaturationAndOwnsItsLifecycle() throws IOException {
        String effect = readClient("blackraven/BlackRavenPerceptionScreenEffects.java");
        String mixin = readClient("mixin/blackraven/BlackRavenGameRendererMixin.java");
        String shader = readResource("assets/minecraft/shaders/program/sparkwitch_perception.fsh");

        assertTrue(effect.contains("DESATURATE_FACTOR = 0.50f"));
        assertTrue(effect.contains("activeProcessor.setUniforms(\"DesaturateFactor\", DESATURATE_FACTOR)"));
        assertTrue(effect.contains("closeProcessor()"));
        assertTrue(effect.contains("processorWidth != framebuffer.textureWidth"));
        assertTrue(mixin.contains("BlackRavenPerceptionScreenEffects.render"));
        assertTrue(shader.contains("mix(color, gray, factor)"));
        assertFalse(effect.contains("sparktraits.client"));
        assertFalse(effect.contains("TraitPlayerComponent"));
    }

    @Test
    void perceptionForcesSteveForOtherPlayersAtBothTextureSeams() throws IOException {
        String broad = readClient("mixin/blackraven/BlackRavenSkinTexturesMixin.java");
        String terminal = readClient("mixin/blackraven/BlackRavenPlayerTextureMixin.java");

        assertTrue(broad.contains("AbstractClientPlayerEntity.class"));
        assertTrue(broad.contains("getSkinTextures"));
        assertTrue(broad.contains("SkinTextures.Model.WIDE"));
        assertTrue(broad.contains("player == client.player"));
        assertTrue(terminal.contains("PlayerEntityRenderer.class"));
        assertTrue(terminal.contains("@ModifyReturnValue"));
        assertTrue(terminal.contains("priority = 400"));
        assertTrue(terminal.contains("textures/entity/player/wide/steve.png"));
        assertFalse(broad.contains("getDisplayName"));
        assertFalse(terminal.contains("getDisplayName"));
    }

    @Test
    void sensedOutlineIsTerminalWhileFeatherMarkRemainsIndependent() throws IOException {
        String gate = readClient("mixin/blackraven/BlackRavenInstinctGateMixin.java");
        String resolver = readClient("mixin/blackraven/BlackRavenInstinctResolverMixin.java");
        String hooks = readClient("blackraven/BlackRavenInstinctClientHooks.java");
        String traits = readClient("blackraven/SparkTraitsInstinctVisibilityBridge.java");

        assertTrue(gate.contains("method = \"isInstinctEnabled\""));
        assertTrue(gate.contains("method = \"isInstinctEnabledAndIsKiller\""));
        assertTrue(gate.contains("at = @At(\"HEAD\")"));
        assertTrue(resolver.contains("@ModifyReturnValue"));
        assertTrue(resolver.contains("method = \"getInstinctHighlight\""));
        assertTrue(resolver.contains("priority = 400"));
        assertTrue(hooks.contains("GetInstinctHighlight.EVENT.register"));
        assertTrue(hooks.contains("InstinctMode.SENSED_ONLY"));
        assertTrue(hooks.contains("return null"));
        assertTrue(hooks.contains("isMarkedForLocalRaven()"));
        assertTrue(hooks.contains("WatheClient.isInstinctEnabled()"));
        assertTrue(hooks.contains("originalColor < 0"));
        assertTrue(traits.contains("dev.caecorthus.sparktraits.api.SparkTraitsApi"));
        assertTrue(traits.contains("isInstinctHidden"));
        assertFalse(traits.contains("sparktraits.component"));
        assertFalse(traits.contains("sparktraits.impl"));
    }

    @Test
    void nearbyIdentityKeepsWatheOrdinaryLivingNameGate() throws IOException {
        String mixin = readClient("mixin/blackraven/BlackRavenRoleNameMixin.java");
        String renderer = readClient("blackraven/BlackRavenRoleNameRenderer.java");

        assertTrue(mixin.contains("@Shadow\n    private static Text nametag"));
        assertTrue(mixin.contains("@Shadow\n    private static float nametagAlpha"));
        assertTrue(mixin.contains(
                "BlackRavenRoleNameRenderer.render(renderer, player, context, nametag, nametagAlpha)"));
        assertFalse(mixin.contains("ProjectileUtil.getCollision"));
        assertTrue(renderer.contains("ProjectileUtil.getCollision"));
        assertTrue(renderer.contains("2.0"));
        assertTrue(renderer.contains("nametag.getString().isBlank()"));
        assertTrue(renderer.contains(
                "getLightLevel(LightType.BLOCK, eyeBlock) < 3\n"
                        + "                && player.getWorld().getLightLevel(LightType.SKY, eyeBlock) < 10"));
        assertTrue(renderer.contains("component.snapshot(target.getUuid())"));
        assertFalse(renderer.contains("canSeeSpectatorInformation"));
        assertFalse(renderer.contains("PlayerBodyEntity"));
    }

    @Test
    void nearbyIdentityHonorsCurrentInstinctSkipWinner() throws IOException {
        String renderer = readClient("blackraven/BlackRavenRoleNameRenderer.java");

        assertTrue(renderer.contains("BlackRavenInstinctClientHooks.isPubliclyVisible(player, target)"));
        assertTrue(renderer.contains("GetInstinctHighlight.EVENT.invoker().getHighlight(target)"));
        assertTrue(renderer.contains("result != null && result.isSkip()"));
    }

    @Test
    void ledgerReadsOnlyCompletedOwnerSnapshotsIntoVanillaBookPages() throws IOException {
        String screen = readClient("blackraven/BlackRavenLedgerScreen.java");

        assertTrue(screen.contains("completedSnapshots()"));
        assertTrue(screen.contains("new BookScreen.Contents"));
        assertTrue(screen.contains("snapshot.playerName()"));
        assertTrue(screen.contains("snapshot.roleTranslationKey()"));
        assertTrue(screen.contains("snapshot.roleColor()"));
        assertFalse(screen.contains("points"));
        assertFalse(screen.contains("partial"));
    }

    @Test
    void allBlackRavenAdaptersAreClientMixins() throws IOException {
        String mixins = Files.readString(CLIENT_RESOURCES.resolve("sparkwitch.client.mixins.json"));

        assertTrue(mixins.contains("blackraven.BlackRavenGameRendererMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenHudMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenInstinctGateMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenInstinctResolverMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenPlayerTextureMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenRoleNameMixin"));
        assertTrue(mixins.contains("blackraven.BlackRavenSkinTexturesMixin"));
    }

    private static String readClient(String relativePath) throws IOException {
        Path path = CLIENT_ROOT.resolve(relativePath);
        assertTrue(Files.exists(path), () -> "Missing client source " + path);
        return Files.readString(path);
    }

    private static String readResource(String relativePath) throws IOException {
        Path path = CLIENT_RESOURCES.resolve(relativePath);
        assertTrue(Files.exists(path), () -> "Missing client resource " + path);
        return Files.readString(path);
    }
}
