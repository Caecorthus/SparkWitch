package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithProgression;
import dev.doctor4t.wathe.api.WatheRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithAssassinGuessCompatibilityTest {
    private static final Path MIXIN_SOURCE = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/AssassinSaboteurGuessMixin.java");
    private static final Path MIXIN_CONFIG = Path.of(
            "src/client/resources/sparkwitch.client.mixins.json");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void bypassesTheNativeKillerFilterForSaboteurOnly() {
        assertTrue(SparkWitchRoles.saboteur().canUseKiller());
        assertFalse(WraithProgression.shouldExcludeFromAssassinGuess(SparkWitchRoles.saboteur()));

        assertTrue(SparkWitchRoles.ninja().canUseKiller());
        assertTrue(WraithProgression.shouldExcludeFromAssassinGuess(SparkWitchRoles.ninja()));
        assertFalse(WraithProgression.shouldExcludeFromAssassinGuess(SparkWitchRoles.windSpirit()));
    }

    @Test
    void redirectsOnlyNoellesNativeKillerFilterAndRegistersTheClientMixin() throws IOException {
        String source = Files.readString(MIXIN_SOURCE);
        assertTrue(source.contains("@Mixin(AssassinScreen.class)"));
        assertTrue(source.contains("method = \"getAllGuessableRoles\""));
        assertTrue(source.contains("target = \"Ldev/doctor4t/wathe/api/Role;canUseKiller()Z\""));
        assertTrue(source.contains("WraithProgression.shouldExcludeFromAssassinGuess(role)"));

        JsonObject config = JsonParser.parseString(Files.readString(MIXIN_CONFIG)).getAsJsonObject();
        JsonArray clientMixins = config.getAsJsonArray("client");
        assertTrue(clientMixins.contains(JsonParser.parseString("\"AssassinSaboteurGuessMixin\"")));
    }

    @Test
    void bundledNoellesFiltersPersistentWraithThroughItsSpecialRoleBoundary() throws IOException {
        assertTrue(WatheRoles.ROLES.contains(SparkWitchRoles.wraith()));
        assertTrue(WatheRoles.SPECIAL_ROLES.contains(SparkWitchRoles.wraith()));
        assertFalse(SparkWitchRoles.assassinGuessRoles().contains(SparkWitchRoles.wraith()));

        try (ZipFile noelles = new ZipFile(noellesRolesJar().toFile())) {
            ClassNode assassinScreen = new ClassNode();
            new ClassReader(noelles.getInputStream(noelles.getEntry(
                    "org/agmas/noellesroles/client/screen/AssassinScreen.class"
            ))).accept(assassinScreen, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            MethodNode guessRoles = assassinScreen.methods.stream()
                    .filter(method -> method.name.equals("getAllGuessableRoles"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(guessRoles);

            int specialRoleFilter = -1;
            int nativeKillerFilter = -1;
            int instructionIndex = 0;
            for (AbstractInsnNode instruction : guessRoles.instructions) {
                if (instruction instanceof FieldInsnNode field
                        && field.owner.equals("dev/doctor4t/wathe/api/WatheRoles")
                        && field.name.equals("SPECIAL_ROLES")) {
                    specialRoleFilter = instructionIndex;
                }
                if (instruction instanceof MethodInsnNode method
                        && method.owner.equals("dev/doctor4t/wathe/api/Role")
                        && method.name.equals("canUseKiller")) {
                    nativeKillerFilter = instructionIndex;
                }
                instructionIndex++;
            }
            assertTrue(specialRoleFilter >= 0);
            assertTrue(nativeKillerFilter > specialRoleFilter);
        }
    }

    private static Path noellesRolesJar() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of("gradle.properties"))) {
            properties.load(input);
        }
        return Path.of("libs", "noellesroles-" + properties.getProperty("noellesroles_version") + ".jar");
    }
}
