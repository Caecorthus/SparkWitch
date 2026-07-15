package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRulesTest {
    @Test
    void exposesApprovedIdentityColorsAndTimings() {
        assertEquals(Identifier.of("sparkwitch", "prophet"), ProphetRules.ROLE_ID);
        assertEquals(Identifier.of("sparkwitch", "death_omen"), ProphetRules.DEATH_OMEN_ID);
        assertEquals(0xD4AF37, ProphetRules.ROLE_COLOR);
        assertEquals(0xFF3030, ProphetRules.CORPSE_HIGHLIGHT_COLOR);
        assertEquals(90, ProphetRules.CORPSE_HIGHLIGHT_PRIORITY);
        assertEquals(1200, ProphetRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(400, ProphetRules.ACTIVE_TICKS);
        assertEquals(1800, ProphetRules.POST_COOLDOWN_TICKS);
    }

    @Test
    void identifiesOnlyTheSparkWitchProphet() {
        Role prophet = role(Identifier.of("sparkwitch", "prophet"));
        Role foreign = role(Identifier.of("example", "prophet"));

        assertTrue(ProphetRules.isProphet(prophet));
        assertFalse(ProphetRules.isProphet(foreign));
        assertFalse(ProphetRules.isProphet(null));
    }

    @Test
    void recordsOnlyCurrentTickBodiesWhoseOwnersAreActuallyDead() {
        assertTrue(ProphetRules.shouldRecordLoadedBody(900, 900, true));
        assertFalse(ProphetRules.shouldRecordLoadedBody(900, 899, true));
        assertFalse(ProphetRules.shouldRecordLoadedBody(900, 900, false));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, false, Role.MoodType.REAL, 200, false);
    }
}
