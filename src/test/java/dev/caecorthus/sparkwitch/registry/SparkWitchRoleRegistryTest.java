package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchRoleRegistryTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoleRegistry.register();
    }

    @Test
    void compatibilityFacadeUsesRegistryStorage() {
        assertSame(SparkWitchRoleRegistry.grandWitch(), SparkWitchRoles.grandWitch());
        assertSame(SparkWitchRoleRegistry.accomplice(), SparkWitchRoles.accomplice());
        assertSame(SparkWitchRoleRegistry.apprenticeWitch(), SparkWitchRoles.apprenticeWitch());
        assertSame(SparkWitchRoleRegistry.murderousWitch(), SparkWitchRoles.murderousWitch());
        assertSame(SparkWitchRoleRegistry.pigGod(), SparkWitchRoles.pigGod());
    }

    @Test
    void registryRefreshMovesAssassinGuessRolesToTailWithoutDuplicates() {
        Role trailingRole = new Role(
                SparkWitch.id("registry_tail_probe"),
                0xFFFFFF,
                true,
                false,
                Role.MoodType.REAL,
                GameConstants.getInTicks(0, 10),
                false
        );
        WatheRoles.ROLES.add(trailingRole);

        try {
            SparkWitchRoleRegistry.refreshAssassinGuessRoleOrder();

            assertEquals(expectedAssassinGuessTail(), currentRoleTail());
            for (Role role : expectedAssassinGuessTail()) {
                assertEquals(1, Collections.frequency(WatheRoles.ROLES, role));
            }
        } finally {
            WatheRoles.ROLES.removeIf(role -> role == trailingRole);
            SparkWitchRoleRegistry.refreshAssassinGuessRoleOrder();
        }
    }

    @Test
    void assassinGuessOrderingOnlyMovesRegisteredRoleInstances() {
        Role lookalikeGrandWitch = new Role(
                SparkWitchRoleRegistry.GRAND_WITCH_ID,
                0xF2DFF7,
                true,
                false,
                Role.MoodType.FAKE,
                -1,
                true
        );
        WatheRoles.ROLES.add(lookalikeGrandWitch);

        try {
            SparkWitchAssassinGuessOrder.appendToTail(SparkWitchRoleRegistry.assassinGuessRoles());

            assertTrue(WatheRoles.ROLES.stream().anyMatch(role -> role == lookalikeGrandWitch));
            assertEquals(expectedAssassinGuessTail(), currentRoleTail());
        } finally {
            WatheRoles.ROLES.removeIf(role -> role == lookalikeGrandWitch);
            SparkWitchRoleRegistry.refreshAssassinGuessRoleOrder();
        }
    }

    private static List<Role> expectedAssassinGuessTail() {
        return List.of(
                SparkWitchRoleRegistry.apprenticeWitch(),
                SparkWitchRoleRegistry.pigGod(),
                SparkWitchRoleRegistry.murderousWitch(),
                SparkWitchRoleRegistry.accomplice(),
                SparkWitchRoleRegistry.grandWitch()
        );
    }

    private static List<Role> currentRoleTail() {
        List<Role> roles = WatheRoles.ROLES;
        return roles.subList(roles.size() - expectedAssassinGuessTail().size(), roles.size());
    }
}
