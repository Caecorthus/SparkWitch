package dev.caecorthus.sparkwitch.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchManaApiTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void clearManaAcceptsNullPlayer() {
        assertDoesNotThrow(() -> WitchManaApi.clearMana(null));
    }

    @Test
    void clearManaIsServerOnlyPublicFacade() throws NoSuchMethodException {
        Method method = WitchManaApi.class.getMethod("clearMana", ServerPlayerEntity.class);

        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void clearManaDelegatesToOwnedComponentStateOperation() throws IOException {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/dev/caecorthus/sparkwitch/api/WitchManaApi.java"
        ));

        assertTrue(source.contains("WitchPlayerComponent.KEY.get(player).clearMana();"));
    }
}
