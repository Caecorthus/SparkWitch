package dev.caecorthus.sparkwitch.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurserWitchFactionRegistrationSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java");

    @Test
    void ownsAllWraithPromotionIdsWithoutAddingThemToOrdinaryRoleMembership() throws IOException {
        String source = Files.readString(REGISTRY);

        assertTrue(source.contains("WRAITH_ID = SparkWitch.id(\"wraith\")"));
        assertTrue(source.contains("WIND_SPIRIT_ID = WindSpiritRole.ID"));
        assertTrue(source.contains("GUARDIAN_ANGEL_ID = GuardianAngelRole.ID"));
        assertTrue(source.contains("VENDETTA_ID = VendettaRole.ID"));
        assertTrue(source.contains("SABOTEUR_ID = SaboteurRole.ID"));
        assertTrue(source.contains("CURSER_ID = CurserRole.ID"));
        assertTrue(source.contains("WatheRoles.SPECIAL_ROLES.add(WraithRole.ROLE)"));
        assertTrue(source.contains("WatheRoles.registerRole(WraithRole.ROLE)"));
        assertTrue(source.contains("SparkFactionApi.registerRole(WindSpiritRole.DEFINITION)"));
        assertTrue(source.contains("SparkFactionApi.registerRole(GuardianAngelRole.DEFINITION)"));
        assertTrue(source.contains("SparkFactionApi.registerRole(VendettaRole.DEFINITION)"));
        assertTrue(source.contains("SparkFactionApi.registerRole(SaboteurRole.DEFINITION)"));
        assertTrue(source.contains("SparkFactionApi.registerRole(CurserRole.DEFINITION)"));
        assertFalse(source.contains("Identifier.of(\"sparktraits\""));
        assertFalse(source.contains("FabricLoader.getInstance().isModLoaded(\"sparktraits\")"));

        int membershipStart = source.indexOf("private static boolean isRegisteredSparkWitchRole");
        assertTrue(membershipStart >= 0);
        String membership = source.substring(membershipStart);
        assertFalse(membership.contains("role == wraith"));
        assertFalse(membership.contains("role == windSpirit"));
        assertFalse(membership.contains("role == guardianAngel"));
        assertFalse(membership.contains("role == vendetta"));
        assertFalse(membership.contains("role == saboteur"));
        assertFalse(membership.contains("role == curser"));
    }
}
