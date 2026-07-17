package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithResponsibilitySplitContractTest {
    @Test
    void entrypointOnlyCoordinatesFocusedServices() throws Exception {
        String source = read("WraithService.java");
        assertTrue(source.contains("WraithTaskService.register();"));
        assertTrue(source.contains("WraithPromotionService.register();"));
        assertTrue(source.contains("WraithSessionService.register();"));
        assertTrue(source.contains("WraithRuntimeService.register();"));
        assertTrue(source.contains("WraithPlayerIsolationService.register();"));
        assertTrue(source.contains("WraithInteractionService.register();"));
        assertTrue(source.contains("WraithDeadPlayerParticipationService.register();"));
        assertTrue(source.contains("WraithFactionService.register();"));
        assertTrue(source.contains("WraithDeferredActivationService.register();"));
        assertFalse(source.contains("TaskComplete.EVENT"));
        assertFalse(source.contains("StatusEffects."));
        assertFalse(source.contains("PlayerBodyEntity"));
    }

    @Test
    void implementationKeepsLifecycleResponsibilitiesSeparate() {
        for (String file : new String[]{
                "WraithDeathService.java", "WraithDeferredActivationService.java",
                "WraithTaskService.java", "WraithPromotionService.java", "WraithSessionService.java",
                "WraithRuntimeService.java", "WraithPlayerIsolationService.java",
                "WraithInteractionService.java", "WraithRoleTransitionService.java",
                "WraithGameModeService.java", "WraithDeadPlayerParticipationService.java",
                "WraithVoiceChannelService.java", "WraithOwnedEffectRules.java"
        }) {
            assertTrue(Files.isRegularFile(path(file)), file);
        }
    }

    private static String read(String file) throws Exception {
        return Files.readString(path(file));
    }

    private static Path path(String file) {
        return Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith", file);
    }
}
