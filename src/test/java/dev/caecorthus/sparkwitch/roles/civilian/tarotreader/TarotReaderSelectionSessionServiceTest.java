package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarotReaderSelectionSessionServiceTest {
    private static final String SERVICE =
            "dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderSelectionSessionService";

    @Test
    void repurchaseOverwritesAndSubmissionConsumesTheSessionOnce() throws Exception {
        UUID player = UUID.randomUUID();
        clearAll();

        open(player, 1);
        open(player, 2);

        Optional<?> session = consume(player);
        assertTrue(session.isPresent());
        assertEquals(2, session.get().getClass().getMethod("mode").invoke(session.get()));
        assertTrue(consume(player).isEmpty());
    }

    @Test
    void paidSessionPersistsUntilConsumedOrClearedExplicitly() throws Exception {
        UUID player = UUID.randomUUID();
        clearAll();

        open(player, 1);
        invoke("clear", new Class<?>[]{UUID.class}, player);
        assertTrue(consume(player).isEmpty());
    }

    private static void open(UUID player, int mode) throws Exception {
        invoke("open", new Class<?>[]{UUID.class, int.class}, player, mode);
    }

    @SuppressWarnings("unchecked")
    private static Optional<?> consume(UUID player) throws Exception {
        return (Optional<?>) invoke("consume", new Class<?>[]{UUID.class}, player);
    }

    private static void clearAll() throws Exception {
        invoke("clearAll", new Class<?>[0]);
    }

    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) throws Exception {
        Class<?> service = Class.forName(SERVICE);
        Method method = service.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
}
