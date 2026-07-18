package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class WitchMaidenRulesTest {
    @Test
    void freezesApprovedIdentityAndGameplayValues() {
        assertEquals("sparkwitch:witch_maiden", WitchMaidenRules.ROLE_ID.toString());
        assertEquals("sparkwitch:focused_footsteps", WitchMaidenRules.FOCUSED_FOOTSTEPS_SKILL_ID.toString());
        assertEquals("noellesroles:voodoo", WitchMaidenRules.VOODOO_DEATH_REASON_ID.toString());
        assertEquals("sparkwitch:tofana_elixir", WitchMaidenRules.TOFANA_DEATH_REASON_ID.toString());
        assertEquals(0xB04A8B, WitchMaidenRules.COLOR);
        assertEquals(1200, WitchMaidenRules.FOCUSED_FOOTSTEPS_INITIAL_COOLDOWN_TICKS);
        assertEquals(600, WitchMaidenRules.FOCUSED_FOOTSTEPS_DURATION_TICKS);
        assertEquals(1800, WitchMaidenRules.FOCUSED_FOOTSTEPS_COOLDOWN_TICKS);
        assertEquals(100, WitchMaidenRules.KNIFE_PRICE);
        assertEquals(50, WitchMaidenRules.LOCKPICK_PRICE);
        assertEquals(75, WitchMaidenRules.POISON_PRICE);
        assertEquals(200, WitchMaidenRules.TOFANA_PRICE);
    }

    @Test
    void recognizesOnlyTheExactWitchMaidenRole() {
        assertTrue(WitchMaidenRules.isWitchMaiden(role(WitchMaidenRules.ROLE_ID)));
        assertFalse(WitchMaidenRules.isWitchMaiden(role(Identifier.of("other", "witch_maiden"))));
        assertFalse(WitchMaidenRules.isWitchMaiden(null));
    }

    @Test
    void blocksOnlyTheExactVoodooDeathForWitchMaiden() {
        assertTrue(WitchMaidenRules.blocksVoodooDeath(
                role(WitchMaidenRules.ROLE_ID),
                Identifier.of("noellesroles", "voodoo")
        ));
        assertFalse(WitchMaidenRules.blocksVoodooDeath(
                role(WitchMaidenRules.ROLE_ID),
                Identifier.of("sparkwitch", "voodoo")
        ));
        assertFalse(WitchMaidenRules.blocksVoodooDeath(
                role(Identifier.of("noellesroles", "voodoo")),
                Identifier.of("noellesroles", "voodoo")
        ));
        assertFalse(WitchMaidenRules.blocksVoodooDeath(null, Identifier.of("noellesroles", "voodoo")));
    }

    @Test
    void tofanaRequiresEveryApprovedRetaliationCondition() {
        assertTrue(WitchMaidenRules.shouldTriggerTofana(true, true, true, true));
        assertFalse(WitchMaidenRules.shouldTriggerTofana(false, true, true, true));
        assertFalse(WitchMaidenRules.shouldTriggerTofana(true, false, true, true));
        assertFalse(WitchMaidenRules.shouldTriggerTofana(true, true, false, true));
        assertFalse(WitchMaidenRules.shouldTriggerTofana(true, true, true, false));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, true, Role.MoodType.FAKE, -1, true);
    }
}
