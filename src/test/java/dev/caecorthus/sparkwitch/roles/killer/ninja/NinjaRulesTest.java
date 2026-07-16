package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaRulesTest {
    @Test
    void exposesApprovedRoleParryAndEconomyTuning() {
        assertEquals(Identifier.of("sparkwitch", "ninja"), NinjaRules.ROLE_ID);
        assertEquals(Identifier.of("sparkwitch", "ninja_parry"), NinjaRules.PARRY_SKILL_ID);
        assertEquals(0x2C2C2C, NinjaRules.COLOR);
        assertEquals(1200, NinjaRules.PARRY_INITIAL_COOLDOWN_TICKS);
        assertEquals(50, NinjaRules.PARRY_WINDOW_TICKS);
        assertEquals(3600, NinjaRules.PARRY_COOLDOWN_TICKS);
        assertEquals(100, NinjaRules.DARK_KILL_BOUNTY);
        assertEquals(100, NinjaRules.NINJA_KNIFE_PRICE);
        assertEquals(275, NinjaRules.NINJA_SHURIKEN_PRICE);
        assertEquals(75, NinjaRules.LOCKPICK_PRICE);
    }

    @Test
    void parryBlocksOnlyDistinctPlayerCausedKillsDuringItsWindow() {
        assertTrue(NinjaRules.shouldParryPlayerKill(
                true, true, false, GameConstants.DeathReasons.KNIFE));
        assertTrue(NinjaRules.shouldParryPlayerKill(
                true, true, false, Identifier.of("example", "custom_player_kill")));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                false, true, false, GameConstants.DeathReasons.KNIFE));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                true, false, false, GameConstants.DeathReasons.KNIFE));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                true, true, true, GameConstants.DeathReasons.KNIFE));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                true, true, false, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                true, true, false, GameConstants.DeathReasons.DROWNED));
        assertFalse(NinjaRules.shouldParryPlayerKill(
                true, true, false, GameConstants.DeathReasons.VANILLA_DEATH));
    }

    @Test
    void darknessUsesRawBrightnessOrWatheBlackout() {
        assertTrue(NinjaRules.isDarkKillLocation(5, false));
        assertTrue(NinjaRules.isDarkKillLocation(15, true));
        assertFalse(NinjaRules.isDarkKillLocation(6, false));
    }

    @Test
    void identifiesOnlyTheNativeSparkWitchNinjaRole() {
        Role ninja = role(Identifier.of("sparkwitch", "ninja"));
        Role upstreamNinja = role(Identifier.of("noellesroles", "ninja"));

        assertTrue(NinjaRules.isNinja(ninja));
        assertFalse(NinjaRules.isNinja(upstreamNinja));
        assertFalse(NinjaRules.isNinja(null));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, true, Role.MoodType.FAKE, -1, true);
    }
}
