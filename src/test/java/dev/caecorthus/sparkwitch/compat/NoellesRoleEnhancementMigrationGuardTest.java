package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodEconomyService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;



class NoellesRoleEnhancementMigrationGuardTest {
    private static final Path EVENTS = Path.of("src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java");
    private static final Path PACKETS = Path.of("src/main/java/dev/caecorthus/sparkwitch/net/SparkWitchPackets.java");
    private static final Path COMPONENTS = Path.of("src/main/java/dev/caecorthus/sparkwitch/component/SparkWitchComponents.java");

    @Test
    void sparkWitchEventsKeepPigGodEconomyButDoNotRegisterMigratedNoellesEnhancements() throws IOException {
        String events = Files.readString(EVENTS);

        assertTrue(events.contains("PigGodEconomyService.register();"));
        assertTrue(events.contains("PigGodEconomyService.assignForRole"));
        assertTrue(events.contains("PigGodEconomyService.onTaskComplete"));
        assertFalse(events.contains("NoellesRoleEnhancementService.register();"));
        assertFalse(events.contains("CorruptCopFeatureService.register();"));
        assertFalse(events.contains("FlashlightBlackoutService.register();"));
    }

    @Test
    void sparkWitchNoLongerOwnsMigratedPacketsOrComponents() throws IOException {
        String packets = Files.readString(PACKETS);
        String components = Files.readString(COMPONENTS);

        assertFalse(packets.contains("SelectCriminologistTargetC2SPacket"));
        assertFalse(packets.contains("OpenCriminologistScreenS2CPacket"));
        assertFalse(components.contains("RoleEnhancementPlayerComponent"));
    }
}
