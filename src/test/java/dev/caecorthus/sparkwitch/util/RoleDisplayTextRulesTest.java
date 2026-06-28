package dev.caecorthus.sparkwitch.util;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoleDisplayTextRulesTest {
    @Test
    void roleTranslationKeyUsesWatheCorpseHudConvention() {
        assertEquals(
                "announcement.role.grand_witch",
                RoleDisplayTextRules.roleTranslationKey(SparkWitchRoles.grandWitch())
        );
    }

    @Test
    void normalizesSlashSeparatedRolePathsForSharedHudLookups() {
        assertEquals(
                "announcement.role.sparkwitch.grand_witch",
                RoleDisplayTextRules.normalizeRoleTranslationKey("announcement.role.sparkwitch/grand_witch")
        );
    }

    @Test
    void missingRoleKeyFallbackIsReadableInsteadOfRawKey() {
        String fallback = RoleDisplayTextRules.fallbackRoleName(
                "announcement.role.the_insane_damned_paranoid_killer_of_doom_death_destruction_and_waffles"
        );

        assertEquals("The Insane Damned Paranoid Killer Of Doom Death Destruction And Waffles", fallback);
        assertFalse(fallback.contains("announcement.role"), fallback);
    }
}
