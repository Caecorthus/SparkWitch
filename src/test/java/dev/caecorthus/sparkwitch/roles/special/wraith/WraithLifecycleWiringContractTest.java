package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithLifecycleWiringContractTest {
    @Test
    void deathListenersOnlyCaptureAndQueueBeforeDeferredEndTickActivation() throws Exception {
        String events = source("impl/SparkWitchEvents.java");
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        assertTrue(events.contains("KillPlayer.BEFORE.register(WraithDeathService::beforeKill)"));
        assertTrue(events.contains("KillPlayer.AFTER.register(WraithDeathService::afterKill)"));
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
    void capturedDeathTimeOwnsCorpseDedupeAcrossDeferredTicks() throws Exception {
        String snapshot = source("roles/special/wraith/WraithDeathSnapshot.java");
        String body = source("roles/special/wraith/WraithBodyService.java");
        String deferred = source("roles/special/wraith/WraithDeferredActivationService.java");
        assertTrue(snapshot.contains("int deathGameTime"));
        assertTrue(body.contains("body.getDeathGameTime() == deathGameTime"));
        assertTrue(deferred.contains("snapshot.deathGameTime()"));
        assertFalse(body.contains("(int) world.getTime()"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }
}
