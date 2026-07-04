package dev.caecorthus.sparkwitch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingGuardTest {
    @Test
    void packagedJarGuardRequiresClientVersionHandshakeClasses() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));

        assertTrue(buildScript.contains("dev/caecorthus/sparkwitch/client/SparkWitchClient.class"));
        assertTrue(buildScript.contains("dev/caecorthus/sparkwitch/net/SparkWitchServerConnection.class"));
        assertTrue(buildScript.contains("dev/caecorthus/sparkwitch/net/SparkWitchPackets.class"));
        assertTrue(buildScript.contains("dev/caecorthus/sparkwitch/net/SparkWitchServerConfirmS2CPacket.class"));
        assertTrue(buildScript.contains(
                "dev/caecorthus/sparkwitch/client/net/SparkWitchClientVersionHandshake.class"
        ));
    }

    @Test
    void playStageConfirmationIsWiredToClientConfirmationGate() throws IOException {
        String packetsSource = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/net/SparkWitchPackets.java"
        ));
        String clientHandshakeSource = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/net/SparkWitchClientVersionHandshake.java"
        ));

        assertTrue(packetsSource.contains("ServerPlayConnectionEvents.JOIN"));
        assertTrue(packetsSource.contains("SparkWitchServerConfirmS2CPacket"));
        assertTrue(clientHandshakeSource.contains("ClientPlayNetworking.registerGlobalReceiver"));
        assertTrue(clientHandshakeSource.contains("SparkWitchVersionCheck.isCompatible"));
        assertTrue(clientHandshakeSource.contains("SparkWitchServerConnection.confirmServer()"));
        assertTrue(clientHandshakeSource.contains("SparkWitchRoles.refreshAssassinGuessRoleOrder()"));
    }
}
