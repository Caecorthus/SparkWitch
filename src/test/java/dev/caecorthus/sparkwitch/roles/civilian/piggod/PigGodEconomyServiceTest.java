package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PigGodEconomyServiceTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void pigGodKeepsGoodRoleMoneyRulesInsideSparkWitch() {
        assertEquals(0, PigGodEconomyService.INITIAL_MONEY);
        assertEquals(50, PigGodEconomyService.TASK_MONEY_REWARD);
        assertTrue(PigGodEconomyService.shouldInitializeMoney(SparkWitchRoles.pigGod()));
        assertTrue(PigGodEconomyService.earnsTaskMoney(SparkWitchRoles.pigGod()));
        assertFalse(PigGodEconomyService.shouldInitializeMoney(WatheRoles.CIVILIAN));
        assertFalse(PigGodEconomyService.earnsTaskMoney(WatheRoles.CIVILIAN));
    }

    @Test
    void pigGodCanStillSeeMoney() {
        assertEquals(
                CanSeeMoney.Result.ALLOW,
                PigGodEconomyService.moneyVisibilityResult(SparkWitchRoles.pigGod())
        );
        assertNull(PigGodEconomyService.moneyVisibilityResult(WatheRoles.CIVILIAN));
        assertNull(PigGodEconomyService.moneyVisibilityResult(null));
    }
}
