package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MurderousWitchRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void murderousWitchHardSkipsOnlyInvisiblePhantoms() {
        Role phantom = role(NoellesRoleIds.PHANTOM);
        Role otherNoellesRole = role(Identifier.of(NoellesRoleIds.NAMESPACE, "shadow_jester"));

        assertEquals(1_000, MurderousWitchRules.HIDDEN_PHANTOM_SKIP_PRIORITY);
        assertTrue(MurderousWitchRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.murderousWitch(), phantom, true));
        assertFalse(MurderousWitchRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.murderousWitch(), phantom, false));
        assertFalse(MurderousWitchRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.murderousWitch(), otherNoellesRole, true));
        assertFalse(MurderousWitchRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.grandWitch(), phantom, true));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, false, Role.MoodType.FAKE, -1, false);
    }
}
