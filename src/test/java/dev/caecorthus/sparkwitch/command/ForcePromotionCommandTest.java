package dev.caecorthus.sparkwitch.command;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcePromotionCommandTest {
    @Test
    void commandLocksFuturePromotionWithoutImmediateWraithMutation() throws Exception {
        String command = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/command/ForcePromotionCommand.java"));
        String queue = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/progression/WraithPromotionQueue.java"));

        assertTrue(command.contains("setForcedWraithPromotion"));
        assertTrue(command.contains("source.getServer().getOverworld()"));
        assertTrue(command.contains("GameWorldComponent.KEY.get(overworld).isRunning()"));
        assertFalse(command.contains("WraithPromotionService.promote(player"));
        assertFalse(command.contains("WraithLifecycle.promotePlayer"));
        assertTrue(queue.contains("player.getServer().getOverworld()"));
        assertTrue(queue.contains("getForcedWraithPromotion"));
        assertTrue(queue.contains("WraithPromotionService.promoteForced"));
        assertTrue(queue.contains("clearForcedWraithPromotion"));
    }

    @Test
    void defaultsUnqualifiedRoleIdsToSparkWitchNamespace() {
        assertEquals(SparkWitchRoles.SABOTEUR_ID,
                ForcePromotionCommand.normalizeRoleId(Identifier.ofVanilla("saboteur")));
        assertEquals(Identifier.of("other", "saboteur"),
                ForcePromotionCommand.normalizeRoleId(Identifier.of("other", "saboteur")));
    }
}
