package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.api.event.KillPlayer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithPriorListenerCaptureContractTest {
    @Test
    void watheBeforeInvokerStopsAfterTheFirstNonNullResult() {
        AtomicBoolean laterListenerCalled = new AtomicBoolean();
        KillPlayer.BEFORE.register((victim, killer, reason) -> KillPlayer.KillResult.allowWithoutBody());
        KillPlayer.BEFORE.register((victim, killer, reason) -> {
            laterListenerCalled.set(true);
            return null;
        });

        KillPlayer.KillResult result = KillPlayer.BEFORE.invoker().beforeKillPlayer(null, null, null);

        assertNotNull(result);
        assertFalse(result.cancelled());
        assertFalse(laterListenerCalled.get());
    }

    @Test
    void mandatoryNoellesSwallowedPathReturnsNonNullAllowWithoutBody() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(
                "libs/noellesroles-1.7.6-h1.5.6-spark.jar").toFile())) {
            byte[] implementation = jar.getInputStream(jar.getEntry(
                    "org/agmas/noellesroles/Noellesroles.class")).readAllBytes();
            String constants = new String(implementation, StandardCharsets.ISO_8859_1);

            assertTrue(constants.contains("SwallowedPlayerComponent"));
            assertTrue(constants.contains("allowWithoutBody"));
        }
    }
}
