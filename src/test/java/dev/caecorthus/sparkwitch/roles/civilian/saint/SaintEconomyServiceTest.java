package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintEconomyServiceTest {
    @Test
    void onlySaintStartsAtZeroAndEarnsFiftyPerTask() {
        Role saint = role("saint");
        Role passenger = role("passenger");

        assertTrue(SaintEconomyService.shouldInitializeMoney(saint));
        assertTrue(SaintEconomyService.earnsTaskMoney(saint));
        assertEquals(0, SaintEconomyService.INITIAL_MONEY);
        assertEquals(50, SaintEconomyService.TASK_MONEY_REWARD);

        assertFalse(SaintEconomyService.shouldInitializeMoney(passenger));
        assertFalse(SaintEconomyService.earnsTaskMoney(passenger));
    }

    @Test
    void onlySaintOptsIntoMoneyVisibility() {
        assertEquals(CanSeeMoney.Result.ALLOW,
                SaintEconomyService.moneyVisibilityResult(role("saint")));
        assertNull(SaintEconomyService.moneyVisibilityResult(role("passenger")));
        assertNull(SaintEconomyService.moneyVisibilityResult(null));
    }

    private static Role role(String path) {
        return new Role(
                Identifier.of("sparkwitch", path),
                0,
                true,
                false,
                Role.MoodType.REAL,
                -1,
                false
        );
    }
}
