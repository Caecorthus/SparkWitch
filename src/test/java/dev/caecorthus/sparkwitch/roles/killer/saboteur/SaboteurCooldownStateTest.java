package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurCooldownStateTest {
    @Test
    void normalizesAndTicksCooldownWithoutCrossingZero() {
        SaboteurCooldownState state = new SaboteurCooldownState();

        assertTrue(state.setCooldownTicks(2));
        assertTrue(state.tick());
        assertEquals(1, state.cooldownTicks());
        assertTrue(state.tick());
        assertEquals(0, state.cooldownTicks());
        assertFalse(state.tick());
        assertFalse(state.setCooldownTicks(-20));
    }

    @Test
    void distinguishesInitialAndPostUseCooldownValues() {
        SaboteurCooldownState state = new SaboteurCooldownState();

        state.setCooldownTicks(1_200);
        assertEquals(1_200, state.cooldownTicks());
        state.setCooldownTicks(2_400);
        assertEquals(2_400, state.cooldownTicks());
    }
}
