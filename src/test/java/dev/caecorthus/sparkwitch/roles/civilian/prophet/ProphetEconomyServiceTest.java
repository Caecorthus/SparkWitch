package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetEconomyServiceTest {
    @Test
    void onlyProphetStartsAtZeroAndEarnsFiftyPerTask() {
        Role prophet = role("prophet");
        Role passenger = role("passenger");

        assertTrue(ProphetEconomyService.shouldInitializeMoney(prophet));
        assertTrue(ProphetEconomyService.earnsTaskMoney(prophet));
        assertEquals(0, ProphetEconomyService.INITIAL_MONEY);
        assertEquals(50, ProphetEconomyService.TASK_MONEY_REWARD);

        assertFalse(ProphetEconomyService.shouldInitializeMoney(passenger));
        assertFalse(ProphetEconomyService.earnsTaskMoney(passenger));
    }

    @Test
    void onlyProphetOptsIntoMoneyVisibility() {
        assertEquals(CanSeeMoney.Result.ALLOW,
                ProphetEconomyService.moneyVisibilityResult(role("prophet")));
        assertNull(ProphetEconomyService.moneyVisibilityResult(role("passenger")));
        assertNull(ProphetEconomyService.moneyVisibilityResult(null));
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
