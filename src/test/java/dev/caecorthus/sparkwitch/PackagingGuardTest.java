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
        assertTrue(buildScript.contains(
                "dev/caecorthus/sparkwitch/client/net/SparkWitchClientVersionHandshake.class"
        ));
    }
}
