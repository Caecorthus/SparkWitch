package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithResponsibilitySplitContractTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final Path WRAITH = MAIN.resolve("roles/special/wraith");

    @Test
    void lifecycleEntrypointCoordinatesDeepModulesWithoutOwningTheirImplementation() throws Exception {
        String lifecycle = readWraith("runtime/WraithLifecycle.java");

        assertTrue(lifecycle.contains("WraithProgression.register();"));
        assertTrue(lifecycle.contains("WraithParticipation.register();"));
        assertTrue(lifecycle.contains("WraithConversion.register();"));
        assertFalse(lifecycle.contains("TaskComplete.EVENT"));
        assertFalse(lifecycle.contains("StatusEffects."));
        assertFalse(lifecycle.contains("PlayerBodyEntity"));
        assertFalse(lifecycle.contains("UseBlockCallback"));
    }

    @Test
    void implementationLivesInResponsibilityOwnedModulesAndRoleDirectories() {
        for (String relative : new String[]{
                "conversion/WraithConversion.java",
                "conversion/WraithDeathSnapshot.java",
                "progression/WraithProgression.java",
                "progression/WraithPromotionQueue.java",
                "progression/WraithPromotionRoles.java",
                "progression/WraithTaskRuntime.java",
                "progression/WraithTaskSnapshot.java",
                "runtime/WraithLifecycle.java",
                "runtime/WraithPresence.java",
                "runtime/WraithParticipation.java"
        }) {
            assertTrue(Files.isRegularFile(WRAITH.resolve(relative)), relative);
        }

        for (String relative : new String[]{
                "roles/civilian/windspirit/WindSpiritRole.java",
                "roles/civilian/guardianangel/GuardianAngelRole.java",
                "roles/civilian/vendetta/VendettaRole.java",
                "roles/killer/saboteur/SaboteurRole.java",
                "roles/witch/curser/CurserRole.java"
        }) {
            assertTrue(Files.isRegularFile(MAIN.resolve(relative)), relative);
        }

        for (String obsolete : new String[]{
                "WraithService.java",
                "WraithDeathService.java",
                "WraithDeferredActivationService.java",
                "WraithTaskService.java",
                "WraithPromotionService.java",
                "WraithSessionService.java",
                "WraithRuntimeService.java"
        }) {
            assertFalse(Files.exists(WRAITH.resolve(obsolete)), obsolete);
        }
    }

    @Test
    void eachWraithEventSeamHasExactlyOneOwner() throws Exception {
        String source = allWraithProductionSource();

        assertOccurrences(source, "TaskComplete.EVENT.register", 1);
        assertOccurrences(source, "ServerPlayConnectionEvents.JOIN.register", 1);
        assertOccurrences(source, "ServerPlayConnectionEvents.DISCONNECT.register", 1);
        assertOccurrences(source, "ServerTickEvents.END_WORLD_TICK.register", 1);
        assertOccurrences(source, "UseBlockCallback.EVENT.register", 1);
        assertOccurrences(source, "UseEntityCallback.EVENT.register", 1);
        assertOccurrences(source, "UseItemCallback.EVENT.register", 1);
        assertOccurrences(source, "DeadPlayerParticipation.EVENT.register", 0);
        String deadParticipation = Files.readString(MAIN.resolve("mixin/WraithDeadParticipationMixin.java"));
        assertTrue(deadParticipation.contains("method = \"serverTick\""));
        assertTrue(deadParticipation.contains("WraithStateService.isActive(player)"));
        assertOccurrences(source, "SparkFactionApi.registerEffectiveFactionResolver", 1);
        assertOccurrences(source, "SparkFactionApi.registerPlayerAffectPolicy", 1);
        assertOccurrences(source, "SparkFactionApi.registerGunPunishmentPolicy", 1);
        assertOccurrences(source, "ServerTickEvents.END_SERVER_TICK.register", 3);
    }

    private static String allWraithProductionSource() throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(WRAITH)) {
            for (Path path : paths.filter(file -> Files.isRegularFile(file)
                    && file.getFileName().toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }

    private static void assertOccurrences(String source, String needle, int expected) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        assertEquals(expected, count, needle);
    }

    private static String readWraith(String relative) throws Exception {
        return Files.readString(WRAITH.resolve(relative));
    }
}
