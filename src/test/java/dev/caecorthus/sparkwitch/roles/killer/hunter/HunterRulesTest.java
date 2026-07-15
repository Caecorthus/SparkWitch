package dev.caecorthus.sparkwitch.roles.killer.hunter;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterRulesTest {
    @Test
    void exposesApprovedRoleShopWeaponAndTrapTuning() {
        assertEquals(Identifier.of("sparkwitch", "hunter"), HunterRules.ROLE_ID);
        assertEquals(0x5C4C34, HunterRules.COLOR);
        assertEquals(100, HunterRules.SHOTGUN_PRICE);
        assertEquals(125, HunterRules.SHELL_PRICE);
        assertEquals(75, HunterRules.TRAP_PRICE);
        assertEquals(1, HunterRules.SHOTGUN_STOCK);
        assertEquals(2, HunterRules.MAX_SHELLS);
        assertEquals(8.0D, HunterRules.SHOTGUN_RANGE);
        assertEquals(4, HunterRules.FOLLOW_UP_COOLDOWN_TICKS);
        assertEquals(600, HunterRules.EMPTY_COOLDOWN_TICKS);
        assertEquals(200, HunterRules.SECOND_SHELL_WINDOW_TICKS);
        assertEquals(2, HunterRules.MAX_OWNED_TRAPS);
        assertEquals(10, HunterRules.TRAP_ARM_TICKS);
        assertEquals(12_000, HunterRules.TRAP_LIFESPAN_TICKS);
        assertEquals(60, HunterRules.TRAP_ROOT_TICKS);
        assertEquals(1_200, HunterRules.FRACTURE_LAYER_TICKS);
        assertEquals(5, HunterRules.MAX_FRACTURE_LAYERS);
        assertEquals(0.20D, HunterRules.SLOW_PER_FRACTURE_LAYER);
        assertEquals(800, HunterRules.TRAP_POISON_TICKS);
        assertEquals(75, HunterRules.POISONER_REWARD);
        assertEquals(50, HunterRules.PLACER_REWARD);
    }

    @Test
    void allReloadPathsShareCooldownCapacityAndSecondShellWindowRules() {
        assertTrue(HunterRules.canReload(0, false, 500L, 0L));
        assertTrue(HunterRules.canReload(1, false, 700L, 700L));
        assertFalse(HunterRules.canReload(1, false, 701L, 700L));
        assertFalse(HunterRules.canReload(2, false, 500L, 700L));
        assertFalse(HunterRules.canReload(0, true, 500L, 0L));

        assertEquals(700L, HunterRules.reloadWindowAfterLoading(0, 500L, 0L));
        assertEquals(700L, HunterRules.reloadWindowAfterLoading(1, 500L, 700L));
        assertEquals(4, HunterRules.cooldownAfterShot(1));
        assertEquals(600, HunterRules.cooldownAfterShot(0));
    }

    @Test
    void trapVisibilityMatchesApprovedViewerMatrix() {
        Identifier vigilante = Identifier.of("wathe", "vigilante");
        Identifier veteran = Identifier.of("wathe", "veteran");
        Identifier corruptCop = Identifier.of("noellesroles", "corrupt_cop");
        Identifier engineer = Identifier.of("noellesroles", "engineer");
        Identifier riotPatrol = Identifier.of("noellesroles", "riot_patrol");
        Identifier grandWitch = Identifier.of("sparkwitch", "grand_witch");
        Identifier accomplice = Identifier.of("sparkwitch", "accomplice");
        Identifier murderousWitch = Identifier.of("sparkwitch", "murderous_witch");
        Identifier apprentice = Identifier.of("sparkwitch", "apprentice_witch");

        assertEquals(HunterRules.TrapVisibility.THROUGH_WALL,
                HunterRules.trapVisibility(null, true, false, false, false));
        assertEquals(HunterRules.TrapVisibility.THROUGH_WALL,
                HunterRules.trapVisibility(null, false, true, false, false));
        assertEquals(HunterRules.TrapVisibility.DIRECT_ONLY,
                HunterRules.trapVisibility(vigilante, false, false, true, false));
        assertEquals(HunterRules.TrapVisibility.HIDDEN,
                HunterRules.trapVisibility(vigilante, false, false, false, false));
        assertEquals(HunterRules.TrapVisibility.DIRECT_ONLY,
                HunterRules.trapVisibility(grandWitch, false, false, true, false));
        assertEquals(HunterRules.TrapVisibility.THROUGH_WALL,
                HunterRules.trapVisibility(grandWitch, false, false, false, true));
        assertEquals(HunterRules.TrapVisibility.THROUGH_WALL,
                HunterRules.trapVisibility(accomplice, false, false, false, true));
        assertEquals(HunterRules.TrapVisibility.THROUGH_WALL,
                HunterRules.trapVisibility(murderousWitch, false, false, false, true));
        assertEquals(HunterRules.TrapVisibility.HIDDEN,
                HunterRules.trapVisibility(apprentice, false, false, true, true));

        assertFalse(HunterRules.canDismantle(vigilante, true));
        assertTrue(HunterRules.canDismantle(veteran, true));
        assertTrue(HunterRules.canDismantle(corruptCop, true));
        assertTrue(HunterRules.canDismantle(engineer, true));
        assertFalse(HunterRules.canDismantle(veteran, false));
        assertFalse(HunterRules.canDismantle(riotPatrol, true));
        assertFalse(HunterRules.canDismantle(grandWitch, true));
    }

    @Test
    void dismantleCooldownRecognizesEveryUpstreamCooldownBearingItem() {
        assertTrue(HunterRules.isExtraDismantleCooldownItem(
                Identifier.of("noellesroles", "double_barrel_shotgun")));
        assertTrue(HunterRules.isExtraDismantleCooldownItem(
                Identifier.of("noellesroles", "riot_shield")));
        assertTrue(HunterRules.isExtraDismantleCooldownItem(
                Identifier.of("noellesroles", "riot_fork")));
        assertFalse(HunterRules.isExtraDismantleCooldownItem(
                Identifier.of("noellesroles", "hunter_trap")));
    }

    @Test
    void poisonRewardsStackWhenOnePlayerFilledBothRoles() {
        HunterRules.TrapPoisonRewards rewards = HunterRules.trapPoisonRewards();

        assertEquals(75, rewards.poisoner());
        assertEquals(50, rewards.placer());
        assertEquals(125, rewards.samePlayerTotal());
    }
}
