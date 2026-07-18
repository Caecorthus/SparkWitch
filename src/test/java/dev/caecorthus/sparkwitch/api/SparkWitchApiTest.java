package dev.caecorthus.sparkwitch.api;

import net.minecraft.entity.player.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchApiTest {
    @Test
    void wraithQueriesArePublicStaticAndNullSafe() throws ReflectiveOperationException {
        assertQuery("isWraithActive");
        assertQuery("isWraithRestricted");
        assertQuery("isWraithPromoted");

        assertFalse(SparkWitchApi.isWraithActive(null));
        assertFalse(SparkWitchApi.isWraithRestricted(null));
        assertFalse(SparkWitchApi.isWraithPromoted(null));
    }

    private static void assertQuery(String name) throws ReflectiveOperationException {
        Method method = SparkWitchApi.class.getDeclaredMethod(name, PlayerEntity.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
    }
}
