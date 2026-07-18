package dev.caecorthus.sparkwitch.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchClientConnectionLifecycleSourceTest {
    private static final Path CLIENT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");

    @Test
    void preservesLoginConfirmationAcrossThePlayTransition() throws IOException {
        String client = Files.readString(CLIENT);

        assertTrue(client.contains(
                "ClientLoginConnectionEvents.INIT.register((handler, client) -> resetConnectionState())"));
        assertTrue(client.contains(
                "ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState())"));
        assertFalse(client.contains(
                "ClientPlayConnectionEvents.INIT.register((handler, client) -> resetConnectionState())"));
        assertTrue(client.contains(
                "ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState())"));
    }
}
