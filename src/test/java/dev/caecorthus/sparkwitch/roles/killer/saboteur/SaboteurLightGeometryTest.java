package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurLightGeometryTest {
    @Test
    void includesBlockCentersOnTheTwentyBlockAxisBoundary() {
        assertTrue(SaboteurLightGeometry.containsBlockCenter(
                0.5D, 0.5D, 0.5D,
                20, 0, 0,
                20.0D
        ));
    }

    @Test
    void excludesCubeCornersOutsideTheEuclideanSphere() {
        assertFalse(SaboteurLightGeometry.containsBlockCenter(
                0.5D, 0.5D, 0.5D,
                20, 20, 0,
                20.0D
        ));
    }

    @Test
    void usesThePlayersExactPositionRatherThanOnlyTheirBlockPosition() {
        assertFalse(SaboteurLightGeometry.containsBlockCenter(
                0.01D, 0.5D, 0.5D,
                20, 0, 0,
                20.0D
        ));
    }
}
