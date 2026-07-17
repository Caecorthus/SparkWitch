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
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        assertFalse(events.contains("KillPlayer.BEFORE.register(WraithDeathService"));
        assertTrue(events.contains("KillPlayer.AFTER.register(WraithDeathService::afterKill)"));
        assertTrue(captureMixin.contains("@Mixin(GameFunctions.class)"));
        assertFalse(captureMixin.contains("remap = false"));
        assertTrue(captureMixin.contains("WraithDeathService.captureBeforeMutation"));
        assertTrue(captureMixin.contains("at = @At(\"HEAD\")"));
        assertTrue(deferred.contains("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(deferred.indexOf("didLastStandTriggerSince")
                < deferred.indexOf("WraithRoundQuotaService.hasCapacity"));
        assertTrue(deferred.indexOf("WraithRoundQuotaService.tryConsume")
                < deferred.indexOf("WraithBodyService.ensureDeathBody"));
    }

    @Test
    void bothListenerOrdersObservePostListenerLastStandState() throws Exception {
        String death = source("roles/special/wraith/WraithDeathService.java");
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        assertTrue(death.contains("SparkTraitsWraithBridge.hasLastStandTriggered"));
        assertFalse(death.contains("activateAfterKill"));
        assertTrue(deferred.contains("snapshot.lastStandTriggeredBefore()"));
    }

    @Test
    void quotaUsesFixedRosterAndRoleReplacementDoesNotFireAssignment() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        String transition = source("roles/special/wraith/WraithRoleTransitionService.java");
        assertTrue(events.contains("gameComponent.getAllPlayers().size()"));
        assertTrue(transition.contains("game.addRole(player, role)"));
        assertTrue(transition.contains("game.sync()"));
        assertTrue(transition.contains("RoleAnnouncementApi.announceCurrentRole(player)"));
        assertFalse(transition.contains("RoleAssigned.EVENT.invoker"));
    }

    @Test
    void resetClearsTraitsWhileWraithStateIsStillActive() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        int reset = events.indexOf("ResetPlayer.EVENT.register");
        int traitClear = events.indexOf("SparkTraitsWraithBridge.clear(serverPlayer, true)", reset);
        int stateClear = events.indexOf("WraithService.clearPlayer(serverPlayer)", reset);

        assertTrue(traitClear > reset);
        assertTrue(stateClear > traitClear);
    }

    @Test
    void capturedDeathTimeOwnsCorpseDedupeAcrossDeferredTicks() throws Exception {
        String snapshot = source("roles/special/wraith/WraithDeathSnapshot.java");
        String body = source("roles/special/wraith/WraithBodyService.java");
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        assertTrue(snapshot.contains("int deathGameTime"));
        assertTrue(body.contains("body.getDeathGameTime() == deathGameTime"));
        assertTrue(deferred.contains("snapshot.deathGameTime()"));
        assertFalse(body.contains("(int) world.getTime()"));
    }

    @Test
    void fallbackCorpseKeepsCapturedRoleAfterAnInterveningLiveRoleChange() throws Exception {
        String body = source("roles/special/wraith/WraithBodyService.java");
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        String accessor = source("mixin/PlayerBodyEntityWraithAccessor.java");

        assertTrue(deferred.contains("snapshot.originalRoleId()"));
        assertTrue(body.contains("Identifier originalRoleId"));
        assertTrue(body.indexOf("body.setPlayerUuid(player.getUuid())")
                < body.indexOf("body.getDataTracker().set"));
        assertTrue(body.contains("originalRoleId.toString()"));
        assertTrue(accessor.contains("@Accessor(\"DEATH_ROLE\")"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }
}
