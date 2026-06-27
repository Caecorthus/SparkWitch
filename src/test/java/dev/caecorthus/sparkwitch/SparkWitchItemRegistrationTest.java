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
    void ceremonialSwordDashUsesPlannedTuning() {
        assertEquals(100, CeremonialSwordItem.DASH_COOLDOWN_TICKS);
        assertEquals(6.0, CeremonialSwordDashService.DASH_DISTANCE_BLOCKS);
    }

    @Test
    void ceremonialSwordUsesDedicatedDeathReason() {
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getNamespace().equals(SparkWitch.MOD_ID));
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getPath().equals("ceremonial_blade"));
    }
}
