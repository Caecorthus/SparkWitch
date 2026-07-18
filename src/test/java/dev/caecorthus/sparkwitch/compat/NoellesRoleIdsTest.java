package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoellesRoleIdsTest {
    @Test
    void recognizesGoodAndKillerPhantomsByStableRoleIdOnly() {
        Role goodPhantom = role(Identifier.of("noellesroles", "phantom"), true, false);
        Role killerPhantom = role(Identifier.of("noellesroles", "phantom"), false, true);
        Role otherRole = role(Identifier.of("noellesroles", "shadow_jester"), false, true);

        assertTrue(NoellesRoleIds.isPhantom(goodPhantom));
        assertTrue(NoellesRoleIds.isPhantom(killerPhantom));
        assertFalse(NoellesRoleIds.isPhantom(otherRole));
        assertFalse(NoellesRoleIds.isPhantom(null));
    }

    @Test
    void recognizesUndercoverByStableRoleIdOnly() {
        Role undercover = role(Identifier.of("noellesroles", "undercover"), true, false);
        Role otherCivilian = role(Identifier.of("noellesroles", "time_keeper"), true, false);

        assertTrue(NoellesRoleIds.isUndercover(undercover));
        assertFalse(NoellesRoleIds.isUndercover(otherCivilian));
        assertFalse(NoellesRoleIds.isUndercover(null));
    }

    private static Role role(Identifier id, boolean innocent, boolean canUseKiller) {
        return new Role(id, 0, innocent, canUseKiller, Role.MoodType.FAKE, -1, false);
    }
}
