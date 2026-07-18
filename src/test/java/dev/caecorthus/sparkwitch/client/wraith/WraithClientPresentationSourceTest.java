package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithClientPresentationSourceTest {
    private static final Path CLIENT_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client"
    );

    @Test
    void viewerPolicyKeepsPeerProjectionPrivateAndModeIndependent() throws IOException {
        String policy = read(CLIENT_ROOT.resolve("wraith/WraithViewerPolicy.java"));
        String rules = read(CLIENT_ROOT.resolve("wraith/WraithViewerRules.java"));

        assertTrue(policy.contains("ANONYMOUS_PEER"));
        assertTrue(policy.contains("HIDDEN"));
        assertTrue(policy.contains("REAL"));
        assertTrue(policy.indexOf("viewerWraithActive") < policy.indexOf("viewerPlayingAndAlive"));
        assertTrue(rules.contains("GameFunctions.isPlayerPlayingAndAlive(viewer)"));
        assertFalse(rules.contains("isSpectator()"));
        assertFalse(rules.contains("isCreative()"));
    }

    @Test
    void renderingUsesViewerLocalSteveAndFinalCyanPeerOutline() throws IOException {
        String projection = read(CLIENT_ROOT.resolve("wraith/WraithSteveProjection.java"));
        String invisibility = read(CLIENT_ROOT.resolve("mixin/wraith/WraithEntityInvisibilityMixin.java"));
        String outlines = read(CLIENT_ROOT.resolve("mixin/wraith/WraithMinecraftClientMixin.java"));
        String highlights = read(CLIENT_ROOT.resolve("mixin/wraith/WraithWatheHighlightMixin.java"));

        assertTrue(projection.contains("textures/entity/player/wide/steve.png"));
        assertTrue(projection.contains("WraithClientState.isActive(viewer)"));
        assertTrue(invisibility.contains("WraithViewerRules.shouldHideFromViewer(viewer, target)"));
        assertTrue(invisibility.contains("WraithViewerRules.shouldRevealWraithTarget(viewer, target)"));
        assertTrue(outlines.indexOf("shouldRevealToWraithPeer") < outlines.indexOf("shouldHideFromViewer"));
        assertTrue(highlights.contains("WraithRole.COLOR"));
        assertFalse(projection.contains("GameProfile"));
    }

    @Test
    void activeWraithOwnsChatTasksGoalsAndPhaseGrayscale() throws IOException {
        String chat = read(CLIENT_ROOT.resolve("mixin/wraith/WraithChatRestrictionMixin.java"));
        String tasks = read(CLIENT_ROOT.resolve("mixin/wraith/WraithMoodRendererMixin.java"));
        String goals = read(CLIENT_ROOT.resolve("mixin/wraith/WraithRoundTextRendererMixin.java"));
        String vision = read(CLIENT_ROOT.resolve("wraith/WraithVisionRules.java"));

        assertTrue(chat.contains("WraithClientState.isActive"));
        assertTrue(chat.contains("GameStatus.INACTIVE"));
        assertTrue(tasks.contains("WraithClientState.isActive"));
        assertTrue(goals.contains("WraithClientState.isRestricted"));
        assertTrue(vision.contains("RESTRICTED_DESATURATION = 1.0f"));
        assertTrue(vision.contains("PROMOTED_DESATURATION = 0.5f"));
    }

    @Test
    void corpseProjectionAndFeatureHidingStayClientLocal() throws IOException {
        String corpses = read(CLIENT_ROOT.resolve("mixin/wraith/WraithCorpseSkinMixin.java"));
        String hiddenBodies = read(CLIENT_ROOT.resolve("mixin/wraith/WraithHiddenBodiesMixin.java"));
        String heldItems = read(CLIENT_ROOT.resolve("mixin/wraith/WraithHeldItemFeatureRendererMixin.java"));
        String names = read(CLIENT_ROOT.resolve("mixin/wraith/WraithNameMixin.java"));

        assertTrue(corpses.contains("WraithSteveProjection.shouldAnonymizeCorpses()"));
        assertTrue(hiddenBodies.contains("cir.setReturnValue(false)"));
        assertTrue(heldItems.contains("WraithSteveProjection.shouldAnonymizePlayer(player)"));
        assertTrue(heldItems.contains("WraithViewerRules.shouldHideFromViewer(viewer, player)"));
        assertTrue(names.contains("ShouldShowCohort.CohortResult.hide(Integer.MAX_VALUE)"));
        assertTrue(names.contains("return Text.literal(\"\")"));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
