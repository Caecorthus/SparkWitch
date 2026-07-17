package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WraithPromotionRolesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void exposesExactAlignmentPoolsInStableOrder() {
        assertEquals(List.of(
                SparkWitchRoles.windSpirit(),
                SparkWitchRoles.guardianAngel(),
                SparkWitchRoles.vendetta()
        ), WraithPromotionRoles.pool(WraithState.Alignment.GOOD));
        assertEquals(List.of(
                SparkWitchRoles.saboteur(),
                SparkWitchRoles.curser()
        ), WraithPromotionRoles.pool(WraithState.Alignment.KILLER));
    }

    @Test
    void picksTheRequestedIndexWithoutRemovingAnIdentityFromThePool() {
        Role goodPick = WraithPromotionRoles.pick(WraithState.Alignment.GOOD, new FixedIndexRandom(2));
        Role killerPick = WraithPromotionRoles.pick(WraithState.Alignment.KILLER, new FixedIndexRandom(1));

        assertEquals(SparkWitchRoles.vendetta(), goodPick);
        assertEquals(SparkWitchRoles.curser(), killerPick);
        assertEquals(3, WraithPromotionRoles.pool(WraithState.Alignment.GOOD).size());
        assertEquals(2, WraithPromotionRoles.pool(WraithState.Alignment.KILLER).size());
    }

    private static final class FixedIndexRandom extends Random {
        private final int index;

        private FixedIndexRandom(int index) {
            this.index = index;
        }

        @Override
        public int nextInt(int bound) {
            return index;
        }
    }
}
