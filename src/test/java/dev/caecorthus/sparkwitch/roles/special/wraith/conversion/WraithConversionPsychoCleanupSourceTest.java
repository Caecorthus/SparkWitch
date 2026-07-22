package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithConversionPsychoCleanupSourceTest {
    @Test
    void cleanupRunsOnlyAfterConversionCommits() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/conversion/WraithConversion.java"
        ));
        int capacityGate = source.indexOf("!round.tryConsume(victim.getUuid())");
        int activation = source.indexOf("wraith.activate(snapshot.alignment())");
        int cleanup = source.indexOf("clearPsychoState(victim)");

        assertTrue(capacityGate >= 0);
        assertTrue(activation > capacityGate);
        assertTrue(cleanup > activation);
        assertTrue(source.contains("psycho.stopPsycho()"));
        assertTrue(source.contains("psycho.sync()"));
    }
}
