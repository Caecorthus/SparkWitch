package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.impl.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.impl.CeremonialSwordCombatService;
import dev.caecorthus.sparkwitch.item.CeremonialSwordItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchItemRegistrationTest {
    @Test
    void ceremonialSwordUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.CEREMONIAL_SWORD_ID.getNamespace());
        assertEquals("ceremonial_sword", SparkWitchItems.CEREMONIAL_SWORD_ID.getPath());
    }

    @Test
    void firePokerUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.FIRE_POKER_ID.getNamespace());
        assertEquals("fire_poker", SparkWitchItems.FIRE_POKER_ID.getPath());
    }

    @Test
    void capsuleUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.CAPSULE_ID.getNamespace());
        assertEquals("capsule", SparkWitchItems.CAPSULE_ID.getPath());
    }

    @Test
    void flashlightUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.FLASHLIGHT_ID.getNamespace());
        assertEquals("flashlight", SparkWitchItems.FLASHLIGHT_ID.getPath());
    }

    @Test
    void ceremonialSwordDashUsesPlannedTuning() {
        assertEquals(100, CeremonialSwordItem.DASH_COOLDOWN_TICKS);
        assertEquals(6.0, CeremonialSwordDashService.DASH_DISTANCE_BLOCKS);
    }

    @Test
    void ceremonialSwordLeftClickUsesConfiguredAttackSpeed() {
        assertEquals(16, CeremonialSwordItem.ATTACK_DAMAGE);
        assertEquals(13, CeremonialSwordItem.ATTACK_DAMAGE_BONUS_VALUE);
        assertEquals(2.0, CeremonialSwordItem.ATTACK_SPEED);
        assertEquals(-2.0f, CeremonialSwordItem.ATTACK_SPEED_MODIFIER_VALUE);
    }

    @Test
    void ceremonialSwordCustomAttackUsesVanillaCooldownBeforeKillGate() {
        CeremonialSwordCombatService.AttackDecision earlySwing =
                CeremonialSwordCombatService.decideAttack(true, false);
        assertTrue(earlySwing.handled());
        assertTrue(earlySwing.resetVanillaCooldown());
        assertFalse(earlySwing.kill());

        CeremonialSwordCombatService.AttackDecision fullSwing =
                CeremonialSwordCombatService.decideAttack(true, true);
        assertTrue(fullSwing.handled());
        assertTrue(fullSwing.resetVanillaCooldown());
        assertTrue(fullSwing.kill());
    }

    @Test
    void ceremonialSwordDashStartGateIsItemOnlyAndNotRoundBound() {
        assertTrue(CeremonialSwordItem.shouldStartDash(true, true, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(false, true, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, false, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, true, true, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, true, false, true));
    }

    @Test
    void ceremonialSwordDashKeepsMovingOutsideWatheRound() {
        assertTrue(CeremonialSwordDashService.shouldKeepDashActive(true, true, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(false, true, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(true, false, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(true, true, true));
    }

    @Test
    void ceremonialSwordUsesDedicatedDeathReason() {
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getNamespace().equals(SparkWitch.MOD_ID));
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getPath().equals("ceremonial_blade"));
    }

    @Test
    void deathRayUsesDedicatedDeathReason() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchDeathReasons.PIERCED_BY_RAY.getNamespace());
        assertEquals("pierced_by_ray", SparkWitchDeathReasons.PIERCED_BY_RAY.getPath());
    }
}
