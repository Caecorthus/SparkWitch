package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithProviderBoundaryTest {
    @Test
    void keepsConfiguredProvidersOnTheirHistoricalBaselines() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        String production = productionSource();
        Path watheJar = Path.of("libs/wathe-1.5.6-spark-1.21.1.jar");
        Path noellesRolesJar = Path.of("libs/noellesroles-1.7.6-h1.5.6-spark.jar");
        Path sparkFactionApiJar = Path.of("libs/sparkfactionapi-0.1.6.0.jar");

        assertTrue(properties.contains("mod_version=0.1.6.2"));
        assertTrue(properties.contains("wathe_version=1.5.6-spark-1.21.1"));
        assertTrue(properties.contains("noellesroles_version=1.7.6-h1.5.6-spark"));
        assertTrue(properties.contains("sparkfactionapi_version=0.1.6.0"));
        assertTrue(Files.isRegularFile(watheJar));
        assertTrue(Files.isRegularFile(noellesRolesJar));
        assertTrue(Files.isRegularFile(sparkFactionApiJar));
        assertEquals("a4e0355c61def0b482c197a7ccd1f86ee91752b7af1b5bdafae8716c652f207f",
                sha256(watheJar));
        assertEquals("fcb0da6995197afff8637dd9236f96d9d07cfc0e26484ad3777e5cf3de37d8b7",
                sha256(noellesRolesJar));
        assertEquals("fce2a6c5e96e95ecb2eef2458de4fdfdcb5765af80be86b3f6d8b0fa7fb73f25",
                sha256(sparkFactionApiJar));
        assertFalse(Files.exists(Path.of("libs/wathe-1.5.7-spark-1.21.1.jar")));
        assertFalse(Files.exists(Path.of("libs/noellesroles-1.7.7-h1.5.7-spark.jar")));

        assertProviderMetadata(watheJar, "wathe", "1.5.6-spark-1.21.1", Map.of());
        assertProviderMetadata(noellesRolesJar, "noellesroles", "1.7.6-h1.5.6-spark",
                Map.of("wathe", "1.5.6-spark-1.21.1"));
        assertProviderMetadata(sparkFactionApiJar, "sparkfactionapi", "0.1.6.0",
                Map.of("wathe", ">=1.5.6-spark-1.21.1"));

        JsonObject sourceMetadata = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/fabric.mod.json"))).getAsJsonObject();
        assertEquals(">=0.1.9.11", sourceMetadata.getAsJsonObject("suggests")
                .get("sparktraits").getAsString());
        assertEquals("<0.1.9.11", sourceMetadata.getAsJsonObject("breaks")
                .get("sparktraits").getAsString());

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

    @Test
    void baselineNoellesSelectorsMatchEveryConsumerOwnedMixin() throws Exception {
        String assassinServerMixin = source("mixin/NoellesAssassinVendettaTargetMixin.java");
        String hiddenEquipmentMixin = source("mixin/NoellesHiddenEquipmentBlackRavenLedgerMixin.java");
        String shadowJesterMixin = source("mixin/NoellesRolesShadowJesterWinMixin.java");
        String throwingAxeMixin = source("mixin/NoellesThrowingAxeVendettaTargetMixin.java");
        String assassinGuessMixin = clientSource("AssassinSaboteurGuessMixin.java");
        String assassinTargetMixin = clientSource("AssassinVendettaTargetMixin.java");
        String hiddenBodiesMixin = clientSource("WraithHiddenBodiesMixin.java");

        assertTrue(assassinServerMixin.contains("method = \"lambda$registerPackets$6\""));
        assertFalse(assassinServerMixin.contains("lambda$registerPackets$37"));
        assertTrue(assassinServerMixin.contains("ordinal = 1"));
        assertTrue(assassinServerMixin.contains("GameFunctions;isPlayerPlayingAndAlive"));
        assertTrue(hiddenEquipmentMixin.contains("@Mixin(HiddenEquipmentHelper.class)"));
        assertTrue(hiddenEquipmentMixin.contains("method = \"filterPacket\""));
        assertTrue(shadowJesterMixin.contains("method = \"lambda$registerEvents$14\""));
        assertTrue(shadowJesterMixin.contains("CheckWinCondition$WinResult;neutralWin"));
        assertTrue(throwingAxeMixin.contains("@Mixin(ThrowingAxeEntity.class)"));
        assertTrue(throwingAxeMixin.contains("method = \"onEntityHit\""));
        assertTrue(throwingAxeMixin.contains("GameFunctions;isPlayerAliveAndSurvival"));
        assertTrue(assassinGuessMixin.contains("@Mixin(AssassinScreen.class)"));
        assertTrue(assassinGuessMixin.contains("method = \"getAllGuessableRoles\""));
        assertTrue(assassinGuessMixin.contains("Role;canUseKiller()Z"));
        assertTrue(assassinTargetMixin.contains("@Mixin(AssassinScreen.class)"));
        assertTrue(assassinTargetMixin.contains("method = \"method_25426\""));
        assertTrue(assassinTargetMixin.contains("GameWorldComponent;getAllAlivePlayers()Ljava/util/List;"));
        assertTrue(hiddenBodiesMixin.contains("@Mixin(value = HiddenBodiesWorldComponent.class"));
        assertTrue(hiddenBodiesMixin.contains("method = \"isHidden\""));

        try (ZipFile noelles = new ZipFile(Path.of(
                "libs/noellesroles-1.7.6-h1.5.6-spark.jar").toFile())) {
            ClassNode noellesroles = classNode(noelles, "org/agmas/noellesroles/Noellesroles.class");
            MethodNode shadowJesterWin = method(noellesroles, "lambda$registerEvents$14",
                    "(Lnet/minecraft/class_3218;Ldev/doctor4t/wathe/cca/GameWorldComponent;"
                            + "Ldev/doctor4t/wathe/game/GameFunctions$WinStatus;)"
                            + "Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;");
            assertNotNull(shadowJesterWin);
            assertEquals(1, invocationCount(shadowJesterWin,
                    "dev/doctor4t/wathe/api/event/CheckWinCondition$WinResult",
                    "neutralWin",
                    "(Lnet/minecraft/class_3222;Ljava/util/List;)"
                            + "Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;"));

            MethodNode assassinPacket = method(noellesroles, "lambda$registerPackets$6",
                    "(Lorg/agmas/noellesroles/packet/AssassinGuessRoleC2SPacket;"
                            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V");
            assertNotNull(assassinPacket);
            assertEquals(2, invocationCount(assassinPacket,
                    "dev/doctor4t/wathe/game/GameFunctions",
                    "isPlayerPlayingAndAlive",
                    "(Lnet/minecraft/class_1657;)Z"));

            ClassNode hiddenEquipment = classNode(noelles,
                    "org/agmas/noellesroles/util/HiddenEquipmentHelper.class");
            assertNotNull(method(hiddenEquipment, "filterPacket",
                    "(Lnet/minecraft/class_2744;Lnet/minecraft/class_1657;Lnet/minecraft/class_3222;)"
                            + "Lnet/minecraft/class_2744;"));

            ClassNode throwingAxe = classNode(noelles,
                    "org/agmas/noellesroles/entity/ThrowingAxeEntity.class");
            MethodNode entityHit = method(throwingAxe, "method_7454",
                    "(Lnet/minecraft/class_3966;)V");
            assertNotNull(entityHit);
            assertEquals(1, invocationCount(entityHit,
                    "dev/doctor4t/wathe/game/GameFunctions",
                    "isPlayerAliveAndSurvival",
                    "(Lnet/minecraft/class_1657;)Z"));

            ClassNode assassinScreen = classNode(noelles,
                    "org/agmas/noellesroles/client/screen/AssassinScreen.class");
            MethodNode guessRoles = method(assassinScreen, "getAllGuessableRoles", "()Ljava/util/List;");
            assertNotNull(guessRoles);
            assertEquals(1, invocationCount(guessRoles,
                    "dev/doctor4t/wathe/api/Role", "canUseKiller", "()Z"));
            MethodNode init = method(assassinScreen, "method_25426", "()V");
            assertNotNull(init);
            assertEquals(1, invocationCount(init,
                    "dev/doctor4t/wathe/cca/GameWorldComponent",
                    "getAllAlivePlayers",
                    "()Ljava/util/List;"));

            ClassNode hiddenBodies = classNode(noelles,
                    "org/agmas/noellesroles/scavenger/HiddenBodiesWorldComponent.class");
            assertNotNull(method(hiddenBodies, "isHidden", "(Ljava/util/UUID;)Z"));
        }
    }

    private static ClassNode classNode(ZipFile jar, String entryName) throws Exception {
        var entry = jar.getEntry(entryName);
        assertNotNull(entry, entryName);
        ClassNode node = new ClassNode();
        new ClassReader(jar.getInputStream(entry)).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElse(null);
    }

    private static int invocationCount(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)
                    && call.desc.equals(descriptor)) {
                count++;
            }
        }
        return count;
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

    private static String clientSource(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin", relative));
    }

    private static void assertProviderMetadata(
            Path jarPath,
            String expectedId,
            String expectedVersion,
            Map<String, String> expectedDependencies
    ) throws Exception {
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            assertNotNull(entry, jarPath.toString());
            JsonObject metadata = JsonParser.parseReader(
                    new java.io.InputStreamReader(jar.getInputStream(entry))).getAsJsonObject();
            assertEquals(expectedId, metadata.get("id").getAsString());
            assertEquals(expectedVersion, metadata.get("version").getAsString());
            JsonObject dependencies = metadata.getAsJsonObject("depends");
            expectedDependencies.forEach((modId, predicate) ->
                    assertEquals(predicate, dependencies.get(modId).getAsString(), modId));
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
