package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoellesRoleEnhancementRulesTest {
    private static final Role DETECTIVE = role("detective");
    private static final Role TOXICOLOGIST = role("toxicologist");
    private static final Role ATTENDANT = role("attendant");
    private static final Role REPORTER = role("reporter");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void criminologistUsesRequestedCostsAndCooldowns() {
        assertEquals(150, NoellesRoleEnhancementRules.CRIMINOLOGIST_COST);
        assertEquals(GameConstants.getInTicks(1, 0), NoellesRoleEnhancementRules.CRIMINOLOGIST_INITIAL_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), NoellesRoleEnhancementRules.CRIMINOLOGIST_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(0, 30), NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS);
        assertEquals(GameConstants.getInTicks(0, 5), NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS);
    }

    @Test
    void enhancedGoodRolesStartAtZeroAndEarnTaskMoney() {
        assertEquals(0, NoellesRoleEnhancementRules.INITIAL_GOOD_ROLE_MONEY);
        assertEquals(50, NoellesRoleEnhancementRules.TASK_MONEY_REWARD);
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(DETECTIVE));
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(TOXICOLOGIST));
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(SparkWitchRoles.pigGod()));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(REPORTER));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(DETECTIVE));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(TOXICOLOGIST));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(SparkWitchRoles.pigGod()));
        assertFalse(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(REPORTER));
        assertTrue(NoellesRoleEnhancementRules.earnsTaskMoney(SparkWitchRoles.pigGod()));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(REPORTER));
    }

    @Test
    void capsuleStaysScopedToRequestedRole() {
        assertEquals(100, NoellesRoleEnhancementRules.CAPSULE_PRICE);
        assertTrue(NoellesRoleEnhancementRules.canBuyCapsules(TOXICOLOGIST));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(DETECTIVE));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(ATTENDANT));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(SparkWitchRoles.pigGod()));
    }

    @Test
    void attendantStartsWithExactlyOneFlashlight() {
        assertTrue(NoellesRoleEnhancementRules.startsWithFlashlight(ATTENDANT));
        assertFalse(NoellesRoleEnhancementRules.startsWithFlashlight(DETECTIVE));
        assertFalse(NoellesRoleEnhancementRules.startsWithFlashlight(TOXICOLOGIST));

        assertTrue(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(ATTENDANT, false));
        assertFalse(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(ATTENDANT, true));
        assertFalse(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(REPORTER, false));
    }

    @Test
    void poisonNameColorsMatchNormalBlueAndMixedStates() {
        assertEquals(0x1E5014, NoellesRoleEnhancementRules.poisonNameColor(true, false));
        assertEquals(0x00BFFF, NoellesRoleEnhancementRules.poisonNameColor(false, true));
        assertEquals(0x0F8789, NoellesRoleEnhancementRules.poisonNameColor(true, true));
    }

    private static Role role(String path) {
        return new Role(
                Identifier.of("noellesroles", path),
                0xFFFFFF,
                false,
                false,
                Role.MoodType.FAKE,
                -1,
                true
        );
    }
}
