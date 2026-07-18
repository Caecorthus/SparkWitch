package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindSpiritRulesTest {
    @Test
    void speedTwoRefreshesOnlyWhenMissingWeakerOrNearlyExpired() {
        assertTrue(WindSpiritRules.shouldRefreshSpeed(-1, 0));
        assertTrue(WindSpiritRules.shouldRefreshSpeed(0, 200));
        assertTrue(WindSpiritRules.shouldRefreshSpeed(1, 20));
        assertFalse(WindSpiritRules.shouldRefreshSpeed(1, 21));
        assertFalse(WindSpiritRules.shouldRefreshSpeed(2, 1));
    }

    @Test
    void onlyAnActivePromotedWindSpiritCrossesTheShopGate() {
        assertTrue(WindSpiritRules.canPassShopAliveGate(true, false, false, false));
        assertTrue(WindSpiritRules.canPassShopAliveGate(false, true, true, false));
        assertFalse(WindSpiritRules.canPassShopAliveGate(false, true, true, true));
        assertFalse(WindSpiritRules.canPassShopAliveGate(false, true, false, false));
        assertFalse(WindSpiritRules.canPassShopAliveGate(false, false, true, false));
    }

    @Test
    void taskIncomeAndBlackoutVisionRequireTheExactPromotedRoleState() {
        assertTrue(WindSpiritRules.shouldRewardTask(true));
        assertFalse(WindSpiritRules.shouldRewardTask(false));
        assertTrue(WindSpiritRules.shouldMaintainBlackoutVision(true, true));
        assertFalse(WindSpiritRules.shouldMaintainBlackoutVision(true, false));
    }
}
