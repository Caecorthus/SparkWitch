package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.impl.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.item.CeremonialSwordItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void ceremonialSwordLeftClickUsesVanillaSwordAttackSpeed() {
        assertEquals(1.6, CeremonialSwordItem.ATTACK_SPEED);
        assertEquals(-2.4f, CeremonialSwordItem.ATTACK_SPEED_MODIFIER_VALUE);
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
