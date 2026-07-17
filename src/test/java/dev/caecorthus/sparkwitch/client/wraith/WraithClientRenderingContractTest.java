package dev.caecorthus.sparkwitch.client.wraith;

import dev.caecorthus.sparkwitch.client.render.WraithVisionRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithClientRenderingContractTest {
    private static final Path CLIENT_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client"
    );

    @Test
    void wraithVisionRulesMapEveryState() {
        assertEquals(0.0f, WraithVisionRules.desaturation(false, false));
        assertEquals(0.0f, WraithVisionRules.desaturation(false, true));
        assertEquals(1.0f, WraithVisionRules.desaturation(true, true));
        assertEquals(0.5f, WraithVisionRules.desaturation(true, false));
    }

    @Test
    void confirmedSparkWitchStateOwnsWraithPresentation() throws IOException {
        String state = readClient("render/WraithClientState.java");

        assertTrue(state.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(state.contains("SparkWitchApi.isWraithActive(player)"));
        assertTrue(state.contains("SparkWitchApi.isWraithRestricted(player)"));
        assertFalse(state.contains("WraithPlayerComponent"));
        assertFalse(state.contains("sparktraits"));
    }

    @Test
    void wraithGrayscaleReusesOneSparkWitchProcessorWithoutStacking() throws IOException {
        String effects = readClient("blackraven/BlackRavenPerceptionScreenEffects.java");
        String mixin = readClient("mixin/blackraven/BlackRavenGameRendererMixin.java");

        int wraith = effects.indexOf("WraithVisionRules.desaturation(");
        int perception = effects.indexOf("BlackRavenClientState.isPerceptionActive(player)");
        assertTrue(wraith >= 0);
        assertTrue(wraith < perception);
        assertTrue(effects.contains("activeProcessor.setUniforms(\"DesaturateFactor\", desaturation)"));
        assertTrue(effects.contains("shaders/post/perception.json"));
        assertTrue(effects.contains("closeProcessor()"));
        assertTrue(mixin.contains("BlackRavenPerceptionScreenEffects.render"));
        assertFalse(effects.contains("depression_insanity"));
    }

    @Test
    void localWraithSeesWideStevePlayersAndCorpsesWithoutIdentityFeatures() throws IOException {
        String projection = readClient("render/WraithSteveProjection.java");
        String skin = readClient("mixin/WraithPlayerSkinMixin.java");
        String skinTextures = readClient("mixin/WraithPlayerSkinTexturesMixin.java");
        String bodySkin = readClient("mixin/WraithCorpseSkinMixin.java");
        String name = readClient("mixin/WraithNameMixin.java");
        String hiddenBodies = readClient("mixin/WraithHiddenBodiesMixin.java");
        String dispatch = readClient("mixin/WraithRendererDispatchMixin.java");
        String playerModel = readClient("mixin/WraithPlayerModelMixin.java");
        String corpseModel = readClient("mixin/WraithCorpseModelMixin.java");
        String cape = readClient("mixin/WraithCapeFeatureRendererMixin.java");
        String elytra = readClient("mixin/WraithElytraFeatureRendererMixin.java");

        assertTrue(projection.contains("textures/entity/player/wide/steve.png"));
        assertTrue(projection.contains("SkinTextures.Model.WIDE"));
        assertTrue(projection.contains("viewer.getUuid().equals(target.getUuid())"));
        assertTrue(skin.contains("WraithSteveProjection.shouldAnonymizePlayer(player)"));
        assertTrue(skinTextures.contains("@Mixin(value = AbstractClientPlayerEntity.class, priority = 2000)"));
        assertTrue(skinTextures.contains("WraithSteveProjection.steveSkinTextures()"));
        assertTrue(bodySkin.contains("WraithSteveProjection.shouldAnonymizeCorpses()"));
        assertTrue(name.contains("return Text.literal(\"\")"));
        assertTrue(name.contains("ShouldShowCohort.CohortResult.hide(Integer.MAX_VALUE)"));
        assertTrue(name.contains("CanSeeBodyRole"));
        assertTrue(hiddenBodies.contains("HiddenBodiesWorldComponent.class"));
        assertTrue(hiddenBodies.contains("cir.setReturnValue(false)"));
        assertTrue(dispatch.contains("modelRenderers.get(SkinTextures.Model.WIDE)"));
        assertTrue(dispatch.contains("new PlayerBodyEntityRenderer<>(context, false)"));
        assertTrue(playerModel.contains("this.model = this.sparkwitch$basePlayerModel"));
        assertTrue(corpseModel.contains("this.model = this.sparkwitch$baseBodyModel"));
        assertTrue(cape.contains("WraithSteveProjection.shouldAnonymizePlayer(player)"));
        assertTrue(elytra.contains("WraithSteveProjection.shouldAnonymizePlayer(player)"));
    }

    @Test
    void onlyActualSpectatorsBypassWraithPrivacy() throws IOException {
        String rules = readClient("render/WraithViewerRules.java");
        String heldItems = readClient("mixin/WraithHeldItemFeatureRendererMixin.java");
        String invisibility = readClient("mixin/WraithEntityInvisibilityMixin.java");
        String outlines = readClient("mixin/WraithMinecraftClientMixin.java");

        assertTrue(rules.contains("!viewer.isSpectator()"));
        assertTrue(rules.contains("viewer.isSpectator()"));
        assertFalse(rules.contains("isCreative()"));
        assertTrue(heldItems.contains("WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, player)"));
        assertTrue(invisibility.contains("WraithViewerRules.shouldRevealToSpectator(viewer, target)"));
        assertTrue(outlines.contains("@Mixin(value = MinecraftClient.class, priority = 100)"));
        assertTrue(outlines.contains("cir.setReturnValue(false)"));
    }

    @Test
    void everyWraithPresentationMixinIsClientRegistered() throws IOException {
        String mixins = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));

        for (String name : new String[]{
                "WraithCapeFeatureRendererMixin",
                "WraithCorpseModelMixin",
                "WraithCorpseSkinMixin",
                "WraithElytraFeatureRendererMixin",
                "WraithEntityInvisibilityMixin",
                "WraithHeldItemFeatureRendererMixin",
                "WraithHiddenBodiesMixin",
                "WraithMinecraftClientMixin",
                "WraithMoodRendererMixin",
                "WraithNameMixin",
                "WraithPlayerModelMixin",
                "WraithPlayerSkinMixin",
                "WraithPlayerSkinTexturesMixin",
                "WraithRendererDispatchMixin",
                "WraithWatheHighlightMixin"
        }) {
            assertTrue(mixins.contains('\"' + name + '\"'), "missing client mixin " + name);
        }
    }

    private static String readClient(String relativePath) throws IOException {
        Path path = CLIENT_ROOT.resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
