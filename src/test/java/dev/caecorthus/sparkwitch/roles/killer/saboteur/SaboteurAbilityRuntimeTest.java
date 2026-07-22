package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurAbilityRuntimeTest {
    @Test
    void successfulRequestExecutesOutageAndStartsTheFullCooldown() {
        AtomicBoolean outageActivated = new AtomicBoolean();
        AtomicInteger cooldown = new AtomicInteger();

        boolean used = SaboteurAbilityRuntime.use(
                true,
                true,
                true,
                () -> outageActivated.set(true),
                cooldown::set
        );

        assertTrue(used);
        assertTrue(outageActivated.get());
        assertEquals(SaboteurRules.COOLDOWN_TICKS, cooldown.get());
    }

    @Test
    void everyServerGateRejectsBeforeAnyEffectOrCooldownMutation() {
        assertRejected(false, true, true);
        assertRejected(true, false, true);
        assertRejected(true, true, false);
    }

    private static void assertRejected(boolean running, boolean promotedSaboteur, boolean ready) {
        AtomicBoolean outageActivated = new AtomicBoolean();
        AtomicInteger cooldown = new AtomicInteger();

        boolean used = SaboteurAbilityRuntime.use(
                running,
                promotedSaboteur,
                ready,
                () -> outageActivated.set(true),
                cooldown::set
        );

        assertFalse(used);
        assertFalse(outageActivated.get());
        assertEquals(0, cooldown.get());
    }
}
