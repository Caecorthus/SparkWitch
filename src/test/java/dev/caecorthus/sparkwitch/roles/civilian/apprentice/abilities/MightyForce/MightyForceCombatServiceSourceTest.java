package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MightyForceCombatServiceSourceTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/apprentice/abilities/MightyForce/MightyForceCombatService.java");
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path ASSIGNMENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/skill/WitchSkillAssignmentService.java");

    @Test
    void revalidatesTheCurrentApprenticeMightyForceOwnershipAtHitTime() throws IOException {
        String source = Files.readString(SERVICE);

        assertTrue(source.contains("gameComponent.getRole(attacker) == SparkWitchRoles.apprenticeWitch()"));
        assertTrue(source.contains("MightyForceAbility.ID.equals(component.getActiveSkillId())"));
    }

    @Test
    void routesTheImpulseAfterTheSynchronousKillOutcomeIsKnown() throws IOException {
        String source = Files.readString(SERVICE);
        int kill = source.indexOf("GameFunctions.killPlayer");
        int impact = source.indexOf("applyKnockback(impactRecipient", kill);

        assertTrue(kill >= 0 && impact > kill);
        assertTrue(source.contains("GameFunctions.isPlayerPlayingAndAlive(target)"));
        assertTrue(source.contains("body.getPlayerUuid().equals(target.getUuid())"));
        assertTrue(source.contains("body.getDeathGameTime() == (int) world.getTime()"));
        assertTrue(source.contains("SparkWitchDeathReasons.MIGHTY_FORCE.equals(body.getDeathReason())"));
        assertTrue(source.contains("!existingBodyUuids.contains(body.getUuid())"));
    }

    @Test
    void changingAwayFromMightyForceCancelsItsWindowWithoutStartingCooldown() throws IOException {
        String assignment = Files.readString(ASSIGNMENT);
        String component = Files.readString(COMPONENT);
        int cancelStart = component.indexOf("public void cancelMightyForceWindow()");
        int cancelEnd = component.indexOf("public void beginSwiftStep", cancelStart);
        String cancelMethod = cancelStart >= 0 && cancelEnd > cancelStart
                ? component.substring(cancelStart, cancelEnd)
                : "";

        assertTrue(assignment.contains("MightyForceAbility.ID.equals(previousSkillId)"));
        assertTrue(assignment.contains("!MightyForceAbility.ID.equals(selectedSkillId)"));
        assertTrue(assignment.contains("component.cancelMightyForceWindow()"));
        assertTrue(cancelMethod.contains("mightyForceTicks = 0"));
        assertTrue(cancelMethod.contains("deferredCooldownTicks = 0"));
        assertFalse(cancelMethod.contains("startDeferredCooldownNow()"));
    }
}
