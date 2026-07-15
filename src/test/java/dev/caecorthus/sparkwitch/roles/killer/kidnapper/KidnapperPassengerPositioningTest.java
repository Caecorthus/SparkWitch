package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KidnapperPassengerPositioningTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void placesTheBodyOneBlockBehindAtCarrierFloorHeight() {
        Vec3d origin = new Vec3d(10.0D, 64.0D, 20.0D);

        assertPosition(new Vec3d(10.0D, 64.0D, 19.0D),
                KidnapperPassengerPositioning.behind(origin, 0.0F));
        assertPosition(new Vec3d(11.0D, 64.0D, 20.0D),
                KidnapperPassengerPositioning.behind(origin, 90.0F));
        assertPosition(new Vec3d(10.0D, 64.0D, 21.0D),
                KidnapperPassengerPositioning.behind(origin, 180.0F));
    }

    private static void assertPosition(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
