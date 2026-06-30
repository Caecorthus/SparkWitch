package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MurderousWitchDeathRayRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void deathRayConstantsMatchDesign() {
        assertEquals(100, MurderousWitchDeathRayRules.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 10), MurderousWitchDeathRayRules.WINDOW_TICKS);
        assertEquals(GameConstants.getInTicks(0, 45), MurderousWitchDeathRayRules.COOLDOWN_TICKS);
        assertEquals(3, MurderousWitchDeathRayRules.MAX_CHARGES);
        assertEquals(10.0, MurderousWitchDeathRayRules.RANGE_BLOCKS);
    }

    @Test
    void onlyMurderousWitchCanSelectDeathRay() {
        assertTrue(MurderousWitchDeathRayRules.canSelect(SparkWitchRoles.murderousWitch()));
        assertFalse(MurderousWitchDeathRayRules.canSelect(SparkWitchRoles.grandWitch()));
        assertFalse(MurderousWitchDeathRayRules.canSelect(SparkWitchRoles.apprenticeWitch()));
        assertFalse(MurderousWitchDeathRayRules.canSelect(WatheRoles.CIVILIAN));
        assertFalse(MurderousWitchDeathRayRules.canSelect(null));
    }

    @Test
    void rayHitsExpandedTargetBoxWithinTenBlocks() {
        Vec3d start = new Vec3d(0.0, 1.6, 0.0);
        Vec3d forward = new Vec3d(1.0, 0.0, 0.0);
        Box target = new Box(7.8, 0.0, -0.3, 8.4, 1.8, 0.3);

        assertTrue(MurderousWitchDeathRayRules.intersectsRay(start, forward, target));
    }

    @Test
    void rayIgnoresTargetsBeyondTenBlocksOrBesideTheBeam() {
        Vec3d start = new Vec3d(0.0, 1.6, 0.0);
        Vec3d forward = new Vec3d(1.0, 0.0, 0.0);

        assertFalse(MurderousWitchDeathRayRules.intersectsRay(
                start,
                forward,
                new Box(10.5, 0.0, -0.3, 11.1, 1.8, 0.3)
        ));
        assertFalse(MurderousWitchDeathRayRules.intersectsRay(
                start,
                forward,
                new Box(6.0, 0.0, 1.0, 6.6, 1.8, 1.6)
        ));
    }

    @Test
    void rayDirectionIsNormalizedForHitChecks() {
        Vec3d start = new Vec3d(0.0, 1.6, 0.0);
        Box target = new Box(7.8, 0.0, -0.3, 8.4, 1.8, 0.3);

        assertTrue(MurderousWitchDeathRayRules.intersectsRay(start, new Vec3d(100.0, 0.0, 0.0), target));
    }
}
