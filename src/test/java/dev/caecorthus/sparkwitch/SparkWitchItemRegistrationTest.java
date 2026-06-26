package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.impl.RitualSwordDashService;
import dev.caecorthus.sparkwitch.item.RitualSwordItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchItemRegistrationTest {
    @Test
    void ritualSwordUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.RITUAL_SWORD_ID.getNamespace());
        assertEquals("ritual_sword", SparkWitchItems.RITUAL_SWORD_ID.getPath());
    }

    @Test
    void ritualSwordDashUsesPlannedTuning() {
        assertEquals(100, RitualSwordItem.DASH_COOLDOWN_TICKS);
        assertEquals(6.0, RitualSwordDashService.DASH_DISTANCE_BLOCKS);
    }

    @Test
    void ritualSwordUsesDedicatedDeathReason() {
        assertTrue(SparkWitchDeathReasons.RITUAL_BLADE.getNamespace().equals(SparkWitch.MOD_ID));
        assertTrue(SparkWitchDeathReasons.RITUAL_BLADE.getPath().equals("ritual_blade"));
    }
}
