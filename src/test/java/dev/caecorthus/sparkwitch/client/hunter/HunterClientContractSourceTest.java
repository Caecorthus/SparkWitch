package dev.caecorthus.sparkwitch.client.hunter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HunterClientContractSourceTest {
    private static final Path CLIENT_ROOT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void trapVisibilityKeepsKillerSpectatorAndWitchRulesExplicit() throws IOException {
        String source = read("hunter/HunterTrapVisibilityHelper.java");

        assertTrue(source.contains("role.getFaction() == Faction.KILLER"));
        assertTrue(source.contains("gameComponent.isRunning()"));
        assertTrue(source.contains("gameComponent.isPlayerDead(viewer.getUuid())"));
        assertTrue(source.contains("viewer.isSpectator() || viewer.isCreative()"));
        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()"));
        assertTrue(source.contains("HunterRules.trapVisibility("));
    }

    @Test
    void trapHighlightStaysBelowSuppressionAndUsesApprovedColors() throws IOException {
        String source = read("hooks/HunterTrapClientHooks.java");

        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.SUPPRESSION_PRIORITY - 1"));
        assertTrue(source.contains("GetInstinctHighlight.EVENT.register"));
        assertTrue(source.contains("HunterRules.COLOR"));
        assertTrue(source.contains("WitchFactionRules.NON_WITCH_INSTINCT_COLOR"));
        assertTrue(source.contains("MurderousWitchRules.INSTINCT_COLOR"));
    }

    @Test
    void trapRendererHidesUnauthorizedViewersButAllowsThroughWallOutline() throws IOException {
        String source = read("renderer/HunterTrapEntityRenderer.java");

        assertTrue(source.contains("HunterTrapVisibilityHelper.visibilityFor("));
        assertTrue(source.contains("case HIDDEN -> false"));
        assertTrue(source.contains("case THROUGH_WALL -> true"));
        assertTrue(source.contains("case DIRECT_ONLY -> super.shouldRender"));
    }

    @Test
    void loadedReadyShotgunUsesWatheTargetCrosshairOnlyWithTarget() throws IOException {
        String source = read("mixin/hunter/DoubleBarrelShotgunCrosshairMixin.java");

        assertTrue(source.contains("instanceof DoubleBarrelShotgunItem"));
        assertTrue(source.contains("DoubleBarrelShotgunItem.getLoadedShells(stack) <= 0"));
        assertTrue(source.contains("player.getItemCooldownManager().isCoolingDown(shotgun)"));
        assertTrue(source.contains("DoubleBarrelShotgunItem.findTarget(player) == null"));
        assertTrue(source.contains("Identifier.of(\"wathe\", \"hud/crosshair_target\")"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(CLIENT_ROOT.resolve(relativePath));
    }
}
