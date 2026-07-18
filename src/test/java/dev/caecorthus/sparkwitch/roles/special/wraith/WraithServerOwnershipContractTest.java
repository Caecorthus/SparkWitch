package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithServerOwnershipContractTest {
    @Test
    void groundPickupChecksAllActiveWraithsRatherThanRestrictedOnly() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/mixin/wraith/WraithGroundPickupMixin.java");
        assertTrue(source.contains("WraithStateService.isActive(player)"));
        assertFalse(source.contains("isRestricted(player)"));
    }

    @Test
    void onlyPromotedWindSpiritCrossesTheDeadShopGate() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/mixin/wraith/WraithPlayerShopComponentMixin.java");
        assertTrue(source.contains("canPassShopAliveGate"));
        assertTrue(source.contains("WraithStateService.isActive(player)"));
        assertTrue(source.contains("WraithStateService.isRestricted(player)"));
    }

    @Test
    void deathResolutionRunsAtCanonicalTailAndChecksCurrentDeathInterception() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithDeathService.java");
        String mixin = read("src/main/java/dev/caecorthus/sparkwitch/mixin/wraith/WraithGameFunctionsMixin.java");
        assertTrue(mixin.contains("at = @At(\"TAIL\")"));
        assertTrue(mixin.contains("finishConfirmedDeath(victim, deathReason)"));
        assertTrue(source.contains("isLastStandDeathIntercepted(player)"));
        assertFalse(source.contains("END_SERVER_TICK"));
        assertFalse(source.contains("KillPlayer.AFTER.register"));
        assertFalse(source.contains("hasTriggeredThisRound"));
        assertFalse(source.contains("ensureDeathBody"));
    }

    @Test
    void swallowedContextIsCapturedBeforeNoellesClearsIt() throws IOException {
        String mixin = read("src/main/java/dev/caecorthus/sparkwitch/mixin/wraith/WraithGameFunctionsMixin.java");
        String capture = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithSwallowedCapture.java");
        String snapshot = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithDeathSnapshot.java");
        assertTrue(mixin.contains("at = @At(\"HEAD\")"));
        assertTrue(capture.contains("SwallowedPlayerComponent"));
        assertTrue(capture.contains("taotie.getPos()"));
        assertTrue(snapshot.contains("MENTAL_BREAKDOWN"));
    }

    @Test
    void pushedFallUsesOnlyWatheLastAttackerHeuristic() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithDeathService.java");
        assertTrue(source.contains("victim.getLastAttacker() instanceof ServerPlayerEntity"));
        assertFalse(source.contains("killer != null ||"));
    }

    @Test
    void successfulActivationClearsRemainingInventoryCoinsAndMana() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithDeathService.java");
        assertTrue(source.contains("player.getInventory().clear()"));
        assertTrue(source.contains("PlayerShopComponent.KEY.get(player).setBalance(0)"));
        assertTrue(source.contains("WitchManaApi.clearMana(player)"));
    }

    @Test
    void fallbackCandidatesStayAboveTheTrainAndDoNotCrossOpenDoors() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithPositionResolver.java");
        assertTrue(source.contains("!isBelowTrain(world, candidate)"));
        assertTrue(source.contains("crossesDoorBoundary(world, anchor, candidate)"));
        assertTrue(source.contains("block instanceof DoorBlock || block instanceof DoorPartBlock"));
    }

    @Test
    void optionalTraitBridgeUsesOnlyPublicGenericFacadeNames() throws IOException {
        String source = read("src/main/java/dev/caecorthus/sparkwitch/compat/SparkTraitsWraithBridge.java");
        assertTrue(source.contains("dev.caecorthus.sparktraits.api.SparkTraitsApi"));
        assertTrue(source.contains("getActiveTraitIds"));
        assertTrue(source.contains("getRevealedTraitIds"));
        assertTrue(source.contains("restoreActiveTraitsForRuntime"));
        assertTrue(source.contains("isLastStandDeathIntercepted"));
        assertFalse(source.contains("sparktraits.component"));
        assertFalse(source.contains("sparktraits.impl"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
