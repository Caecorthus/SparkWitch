package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithProviderBoundaryTest {
    @Test
    void keepsWatheAndNoellesOnTheirUnmodifiedBaselines() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        String production = productionSource();

        assertTrue(properties.contains("wathe_version=1.5.6-spark-1.21.1"));
        assertTrue(properties.contains("noellesroles_version=1.7.6-h1.5.6-spark"));
        assertTrue(Files.isRegularFile(Path.of("libs/wathe-1.5.6-spark-1.21.1.jar")));
        assertTrue(Files.isRegularFile(Path.of("libs/noellesroles-1.7.6-h1.5.6-spark.jar")));
        assertEquals("a4e0355c61def0b482c197a7ccd1f86ee91752b7af1b5bdafae8716c652f207f",
                sha256(Path.of("libs/wathe-1.5.6-spark-1.21.1.jar")));
        assertEquals("fcb0da6995197afff8637dd9236f96d9d07cfc0e26484ad3777e5cf3de37d8b7",
                sha256(Path.of("libs/noellesroles-1.7.6-h1.5.6-spark.jar")));
        assertFalse(Files.exists(Path.of("libs/wathe-1.5.7-spark-1.21.1.jar")));
        assertFalse(Files.exists(Path.of("libs/noellesroles-1.7.7-h1.5.7-spark.jar")));
        assertFalse(production.contains("dev.doctor4t.wathe.api.RoleAnnouncementApi"));
        assertFalse(production.contains("dev.doctor4t.wathe.api.event.DeadPlayerParticipation"));
        assertFalse(production.contains("WatheRoles.registerSpecialRole"));
        assertFalse(production.contains("PlayerBodyEntityWraithAccessor"));
    }

    @Test
    void ownsFormerProviderSeamsInsideSparkWitch() throws Exception {
        String bodyRoleNameMixin = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/WraithBodyRoleNameMixin.java"
        ));
        assertTrue(source("mixin/WraithDeadParticipationMixin.java")
                .contains("WraithStateService.isActive(player)"));
        assertTrue(source("net/WraithRoleAnnouncementS2CPacket.java")
                .contains("wraith_role_announcement"));
        assertTrue(source("roles/special/wraith/runtime/WraithRoleAnnouncementService.java")
                .contains("WraithRoleAnnouncementS2CPacket"));
        assertTrue(source("mixin/PlayerBodyEntityWraithRoleMixin.java")
                .contains("SparkWitchDeathRole"));
        assertTrue(bodyRoleNameMixin.contains("@ModifyExpressionValue"));
        assertTrue(bodyRoleNameMixin.contains(
                "return WraithBodyRoleResolver.resolve(body, currentRole)"));
    }

    private static String productionSource() throws Exception {
        StringBuilder source = new StringBuilder();
        for (Path root : new Path[]{Path.of("src/main/java"), Path.of("src/client/java")}) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(file -> Files.isRegularFile(file)
                        && file.getFileName().toString().endsWith(".java")).toList()) {
                    source.append(Files.readString(path)).append('\n');
                }
            }
        }
        return source.toString();
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
