package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfumerEconomyServiceTest {
    @Test
    void onlyPerfumerStartsAtZeroAndEarnsFiftyPerTask() {
        Role perfumer = role("perfumer");
        Role passenger = role("passenger");

        assertTrue(PerfumerEconomyService.shouldInitializeMoney(perfumer));
        assertTrue(PerfumerEconomyService.earnsTaskMoney(perfumer));
        assertEquals(0, PerfumerEconomyService.INITIAL_MONEY);
        assertEquals(50, PerfumerEconomyService.TASK_MONEY_REWARD);

        assertFalse(PerfumerEconomyService.shouldInitializeMoney(passenger));
        assertFalse(PerfumerEconomyService.earnsTaskMoney(passenger));
    }

    @Test
    void onlyPerfumerOptsIntoMoneyVisibility() {
        assertEquals(CanSeeMoney.Result.ALLOW,
                PerfumerEconomyService.moneyVisibilityResult(role("perfumer")));
        assertNull(PerfumerEconomyService.moneyVisibilityResult(role("passenger")));
        assertNull(PerfumerEconomyService.moneyVisibilityResult(null));
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
