package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionCapabilities;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleSelectionContext;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchRoleRegistrationTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void witchFactionRolesResolveToWitchFaction() {
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.grandWitch()));
        assertEquals(SparkWitchFactions.WITCH, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.accomplice()));
    }

    @Test
    void witchFactionUsesConfiguredColor() {
        assertEquals(0xE9D5F0, SparkFactionApi.getFaction(SparkWitchFactions.WITCH).orElseThrow().color());
    }

    @Test
    void witchFactionUsesExplicitKillerLikeCapabilitiesWithoutNativeKillerBucket() {
        FactionCapabilities capabilities = SparkFactionApi.getFaction(SparkWitchFactions.WITCH)
                .orElseThrow()
                .capabilities();

        assertFalse(SparkWitchRoles.grandWitch().canUseKiller());
        assertFalse(SparkWitchRoles.accomplice().canUseKiller());
        assertFalse(capabilities.canUseKillerFeatures());
        assertTrue(capabilities.isPunishableInnocentGunShooter());
        assertFalse(capabilities.isPunishableInnocentGunVictim());
        assertTrue(capabilities.receivesKillerPassiveMoney());
        assertTrue(capabilities.receivesKillRewards());
        assertTrue(capabilities.hasBlackoutImmunity());
        assertTrue(capabilities.sharesCohort());
        assertTrue(capabilities.canUseInstinct());
        assertEquals(0x36E51B, capabilities.instinctColor());
    }

    @Test
    void witchRolesUseConfiguredColors() {
        assertEquals(0xF2DFF7, SparkWitchRoles.grandWitch().color());
        assertEquals(0x6B338A, SparkWitchRoles.accomplice().color());
        assertEquals(0x75EDFA, SparkWitchRoles.apprenticeWitch().color());
        assertEquals(0x7A3857, SparkWitchRoles.murderousWitch().color());
        assertEquals(0xF2A4FC, SparkWitchRoles.pigGod().color());
    }

    @Test
    void witchRolesUsePlannedStamina() {
        assertEquals(-1, SparkWitchRoles.grandWitch().getMaxSprintTime());
        assertEquals(-1, SparkWitchRoles.accomplice().getMaxSprintTime());
        assertEquals(GameConstants.getInTicks(0, 10), SparkWitchRoles.apprenticeWitch().getMaxSprintTime());
        assertEquals(-1, SparkWitchRoles.murderousWitch().getMaxSprintTime());
        assertEquals(GameConstants.getInTicks(0, 10), SparkWitchRoles.pigGod().getMaxSprintTime());
    }

    @Test
    void grandWitchFactionCanSeeRemainingGameTime() {
        assertTrue(SparkWitchRoles.grandWitch().canSeeTime());
        assertTrue(SparkWitchRoles.accomplice().canSeeTime());
    }

    @Test
    void witchRoleAppearanceConditionsUsePerRoleThresholds() {
        assertFalse(shouldAppearAt(SparkWitchRoles.grandWitch(), 17));
        assertFalse(shouldAppearAt(SparkWitchRoles.accomplice(), 17));
        assertFalse(shouldAppearAt(SparkWitchRoles.apprenticeWitch(), 17));
        assertFalse(shouldAppearAt(SparkWitchRoles.murderousWitch(), 17));
        assertTrue(shouldAppearAt(SparkWitchRoles.pigGod(), 17));

        assertTrue(shouldAppearAt(SparkWitchRoles.grandWitch(), 18));
        assertTrue(shouldAppearAt(SparkWitchRoles.accomplice(), 18));
        assertFalse(shouldAppearAt(SparkWitchRoles.apprenticeWitch(), 23));
        assertFalse(shouldAppearAt(SparkWitchRoles.murderousWitch(), 23));
        assertTrue(shouldAppearAt(SparkWitchRoles.apprenticeWitch(), 24));
        assertTrue(shouldAppearAt(SparkWitchRoles.murderousWitch(), 24));
        assertTrue(shouldAppearAt(SparkWitchRoles.pigGod(), 18));
        assertTrue(shouldAppearAt(SparkWitchRoles.pigGod(), 24));
    }

    @Test
    void apprenticeWitchIsNativeCivilianRole() {
        assertTrue(SparkWitchRoles.apprenticeWitch().isInnocent());
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.apprenticeWitch()));
    }

    @Test
    void pigGodResolvesToCivilianFaction() {
        assertTrue(SparkWitchRoles.pigGod().isInnocent());
        assertFalse(SparkWitchRoles.pigGod().canUseKiller());
        assertEquals(Faction.CIVILIAN, SparkWitchRoles.pigGod().getFaction());
        assertEquals(FactionIds.CIVILIAN, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.pigGod()));
    }

    @Test
    void murderousWitchIsNativeNeutralRole() {
        assertTrue(SparkWitchRoles.murderousWitch().isNeutral());
        assertEquals(FactionIds.NEUTRAL, SparkFactionApi.resolveBaseFaction(SparkWitchRoles.murderousWitch()));
    }

    @Test
    void murderousWitchEffectiveFactionIsRegisteredForExplicitBridgesOnly() {
        FactionCapabilities capabilities = SparkFactionApi.getFaction(SparkWitchFactions.MURDEROUS_WITCH)
                .orElseThrow()
                .capabilities();

        assertFalse(SparkWitchRoles.murderousWitch().canUseKiller());
        assertFalse(capabilities.canUseKillerFeatures());
        assertTrue(capabilities.receivesKillerPassiveMoney());
        assertTrue(capabilities.receivesKillRewards());
        assertTrue(capabilities.hasBlackoutImmunity());
        assertFalse(capabilities.sharesCohort());
        assertTrue(capabilities.canUseInstinct());
        assertEquals(0xC13838, capabilities.instinctColor());
    }

    @Test
    void assassinGuessPanelRolesAppendToWatheRoleTail() {
        assertEquals(expectedAssassinGuessTail(), currentRoleTail());
    }

    @Test
    void repeatedRegistrationMovesAssassinGuessPanelRolesBackToTailWithoutDuplicates() {
        Role trailingRole = new Role(
                SparkWitch.id("assassin_tail_probe"),
                0xFFFFFF,
                true,
                false,
                Role.MoodType.REAL,
                GameConstants.getInTicks(0, 10),
                false
        );
        WatheRoles.ROLES.add(trailingRole);

        try {
            SparkWitchRoles.register();

            assertEquals(expectedAssassinGuessTail(), currentRoleTail());
            for (Role role : expectedAssassinGuessTail()) {
                assertEquals(1, Collections.frequency(WatheRoles.ROLES, role));
            }
        } finally {
            WatheRoles.ROLES.remove(trailingRole);
            SparkWitchRoles.register();
        }
    }

    private static List<Role> expectedAssassinGuessTail() {
        return List.of(
                SparkWitchRoles.apprenticeWitch(),
                SparkWitchRoles.pigGod(),
                SparkWitchRoles.murderousWitch(),
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.grandWitch()
        );
    }

    private static List<Role> currentRoleTail() {
        List<Role> roles = WatheRoles.ROLES;
        return roles.subList(roles.size() - expectedAssassinGuessTail().size(), roles.size());
    }

    private static boolean shouldAppearAt(Role role, int totalPlayers) {
        return role.shouldAppear(new RoleSelectionContext(null, null, List.of(), totalPlayers, 0, 0, 0));
    }
}
