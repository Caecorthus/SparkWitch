package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KidnapperRulesTest {
    @Test
    void exposesOnlyTheApprovedCorpseDragTuning() {
        assertEquals(Identifier.of("sparkwitch", "kidnapper"), KidnapperRules.ROLE_ID);
        assertEquals(Identifier.of("sparkwitch", "kidnapper_drag_body"), KidnapperRules.DRAG_BODY_SKILL_ID);
        assertEquals(Identifier.of("sparkwitch", "kidnapper_drag_speed"), KidnapperRules.SPEED_MODIFIER_ID);
        assertEquals(0x9B59B6, KidnapperRules.COLOR);
        assertEquals(2.0D, KidnapperRules.TARGET_RANGE);
        assertEquals(4.0D, KidnapperRules.TARGET_RANGE_SQUARED);
        assertEquals(1.0D, KidnapperRules.FOLLOW_DISTANCE);
        assertEquals(0.8D, KidnapperRules.THROW_SPEED);
        assertEquals(0.2D, KidnapperRules.THROW_MIN_UPWARD_VELOCITY);
        assertEquals(0.8D, KidnapperRules.SPEED_MULTIPLIER);
        assertEquals(-0.2D, KidnapperRules.SPEED_MODIFIER_AMOUNT);
        assertEquals(0.8D, KidnapperRules.THROW_SPEED);
        assertEquals(0.2D, KidnapperRules.THROW_MIN_UPWARD_VELOCITY);
    }

    @Test
    void identifiesOnlyTheSparkWitchKidnapperRole() {
        assertTrue(KidnapperRules.isKidnapper(role(Identifier.of("sparkwitch", "kidnapper"))));
        assertFalse(KidnapperRules.isKidnapper(role(Identifier.of("example", "kidnapper"))));
        assertFalse(KidnapperRules.isKidnapper(null));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, true, Role.MoodType.FAKE, -1, true);
    }
}
