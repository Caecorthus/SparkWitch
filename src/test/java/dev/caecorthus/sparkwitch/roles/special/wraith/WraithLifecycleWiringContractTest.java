package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithLifecycleWiringContractTest {
    @Test
    void deathCapturePrecedesAllBeforeListenersAndAfterOnlyQueuesForEndTick() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        String captureMixin = source("mixin/WraithDeathCaptureMixin.java");
        String conversion = source("roles/special/wraith/conversion/WraithConversion.java");
        assertFalse(events.contains("KillPlayer.BEFORE.register(WraithConversion"));
        assertTrue(events.contains("KillPlayer.AFTER.register(WraithConversion::afterKill)"));
        assertTrue(captureMixin.contains("@Mixin(GameFunctions.class)"));
        assertFalse(captureMixin.contains("remap = false"));
        assertTrue(captureMixin.contains("WraithConversion.captureBeforeMutation"));
        assertTrue(captureMixin.contains("at = @At(\"HEAD\")"));
        assertTrue(conversion.contains("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(conversion.indexOf("didLastStandTriggerSince")
                < conversion.indexOf("round.hasCapacity"));
        assertTrue(conversion.indexOf("round.tryConsume")
                < conversion.indexOf("ensureDeathBody"));
    }

    @Test
    void bothListenerOrdersObservePostListenerLastStandState() throws Exception {
        String conversion = source("roles/special/wraith/conversion/WraithConversion.java");
        assertTrue(conversion.contains("SparkTraitsWraithBridge.hasLastStandTriggered"));
        assertFalse(conversion.contains("activateAfterKill"));
        assertTrue(conversion.contains("snapshot.lastStandTriggeredBefore()"));
    }

    @Test
    void quotaUsesFixedRosterAndRoleReplacementDoesNotFireAssignment() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        assertTrue(events.contains("WraithConversion.beginRound(serverWorld, gameComponent.getAllPlayers().size())"));
        assertTrue(lifecycle.contains("game.addRole(player, role)"));
        assertTrue(lifecycle.contains("game.sync()"));
        assertTrue(lifecycle.contains("WraithRoleAnnouncementService.announceCurrentRole(player)"));
        assertFalse(lifecycle.contains("dev.doctor4t.wathe.api.RoleAnnouncementApi"));
        assertFalse(lifecycle.contains("RoleAssigned.EVENT.invoker"));
    }

    @Test
    void resetClearsTraitsWhileWraithStateIsStillActive() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        int reset = events.indexOf("ResetPlayer.EVENT.register");
        int traitClear = events.indexOf("SparkTraitsWraithBridge.clear(serverPlayer, true)", reset);
        int stateClear = events.indexOf("WraithLifecycle.clearPlayer(serverPlayer)", reset);

        assertTrue(traitClear > reset);
        assertTrue(stateClear > traitClear);
    }

    @Test
    void capturedDeathTimeOwnsCorpseDedupeAcrossDeferredTicks() throws Exception {
        String snapshot = source("roles/special/wraith/conversion/WraithDeathSnapshot.java");
        String conversion = source("roles/special/wraith/conversion/WraithConversion.java");
        assertTrue(snapshot.contains("int deathGameTime"));
        assertTrue(conversion.contains("body.getDeathGameTime() == deathGameTime"));
        assertTrue(conversion.contains("snapshot.deathGameTime()"));
        assertFalse(conversion.contains("(int) world.getTime()"));
    }

    @Test
    void existingAndFallbackCorpsesKeepCapturedRoleAfterAnInterveningLiveRoleChange() throws Exception {
        String conversion = source("roles/special/wraith/conversion/WraithConversion.java");
        String bodyMixin = source("mixin/PlayerBodyEntityWraithRoleMixin.java");
        String access = source("roles/special/wraith/conversion/WraithBodyRoleAccess.java");

        assertTrue(conversion.contains("snapshot.originalRoleId()"));
        assertTrue(conversion.contains("Identifier originalRoleId"));
        assertTrue(conversion.contains(
                "((WraithBodyRoleAccess) existingBody).sparkwitch$setDeathRole(originalRoleId)"));
        assertTrue(conversion.indexOf("body.setPlayerUuid(player.getUuid())")
                < conversion.lastIndexOf("sparkwitch$setDeathRole(originalRoleId)"));
        assertTrue(bodyMixin.contains("SparkWitchDeathRole"));
        assertTrue(bodyMixin.contains("TrackedDataHandlerRegistry.STRING"));
        assertTrue(access.contains("sparkwitch$getDeathRole"));
        assertFalse(bodyMixin.contains("@Accessor(\"DEATH_ROLE\")"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }
}
