package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KidnapperThrowServiceTest {
    @Test
    void scalesHorizontalViewAndAddsTheMinimumUpwardVelocity() {
        assertEquals(
                new Vec3d(0.8D, 0.2D, 0.0D),
                KidnapperThrowService.throwVelocity(new Vec3d(1.0D, 0.0D, 0.0D))
        );
    }

    @Test
    void preservesAnUpwardThrowAboveTheMinimum() {
        assertEquals(
                new Vec3d(0.0D, 0.8D, 0.0D),
                KidnapperThrowService.throwVelocity(new Vec3d(0.0D, 1.0D, 0.0D))
        );
    }

    @Test
    void clampsOnlyTheDownwardAxis() {
        assertEquals(
                new Vec3d(0.0D, 0.2D, 0.0D),
                KidnapperThrowService.throwVelocity(new Vec3d(0.0D, -1.0D, 0.0D))
        );
    }
}
