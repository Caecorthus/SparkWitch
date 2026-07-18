package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianAngelStateTest {
    @Test
    void promotionStartsSixtySecondCooldownAndSuccessfulCastStartsNinetySeconds() {
        GuardianAngelState state = new GuardianAngelState();
        UUID targetUuid = UUID.randomUUID();

        state.initializeForPromotion();
        assertEquals(1_200, state.cooldownTicks());
        assertFalse(state.assignShield(targetUuid));

        tick(state, GuardianAngelRules.INITIAL_COOLDOWN_TICKS);
        assertTrue(state.assignShield(targetUuid));
        assertEquals(targetUuid, state.shieldTargetUuid());
        assertEquals(1_800, state.cooldownTicks());
        assertFalse(state.assignShield(UUID.randomUUID()));
    }

    @Test
    void consumingOrExpiringShieldClearsOneTargetWithoutErasingCooldown() {
        GuardianAngelState state = new GuardianAngelState();
        UUID targetUuid = UUID.randomUUID();
        state.restore(0, null);
        assertTrue(state.assignShield(targetUuid));

        assertFalse(state.clearShieldTarget(UUID.randomUUID()));
        assertTrue(state.clearShieldTarget(targetUuid));
        assertNull(state.shieldTargetUuid());
        assertEquals(GuardianAngelRules.POST_USE_COOLDOWN_TICKS, state.cooldownTicks());
    }

    @Test
    void clearDropsAllOwnerPrivateState() {
        GuardianAngelState state = new GuardianAngelState();
        state.restore(37, UUID.randomUUID());

        assertTrue(state.clear());
        assertEquals(0, state.cooldownTicks());
        assertNull(state.shieldTargetUuid());
        assertFalse(state.clear());
    }

    private static void tick(GuardianAngelState state, int ticks) {
        for (int index = 0; index < ticks; index++) {
            state.tickCooldown();
        }
    }
}
