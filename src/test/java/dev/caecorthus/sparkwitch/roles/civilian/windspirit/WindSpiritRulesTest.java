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

    @Test
    void promotedWindSpiritChargeRestoresAnOrdinaryParticipatingPlayerHit() {
        assertTrue(resolve(false, true, true, false, true, true, true, false, false));
        assertTrue(resolve(true, true, true, false, true, true, true, false, false));
    }

    @Test
    void everyActiveWraithStageRemainsExcludedEvenWhenVanillaWouldAllowTheHit() {
        for (String stage : new String[]{
                "base", "wind_spirit", "guardian_angel", "vendetta", "saboteur", "curser"
        }) {
            assertFalse(resolve(false, true, true, false, true, true, true, false, true), stage);
            assertFalse(resolve(true, true, true, false, true, true, true, false, true), stage);
        }
    }

    @Test
    void ownerSpectatorDeadRemovedAndNonParticipatingPlayersRemainExcluded() {
        for (boolean vanillaCanHit : new boolean[]{false, true}) {
            assertFalse(resolve(vanillaCanHit, true, true, true, true, true, true, false, false));
            assertFalse(resolve(vanillaCanHit, true, true, false, true, true, true, true, false));
            assertFalse(resolve(vanillaCanHit, true, true, false, true, false, true, false, false));
            assertFalse(resolve(vanillaCanHit, true, true, false, true, true, false, false, false));
        }
    }

    @Test
    void breezeOrdinaryPlayerAndNonPlayerEligibilityRemainVanillaOwned() {
        assertFalse(resolve(false, false, true, false, true, true, true, false, false));
        assertTrue(resolve(true, false, true, false, true, true, true, false, false));
        assertFalse(resolve(false, true, false, false, true, true, true, false, false));
        assertTrue(resolve(true, true, false, false, true, true, true, false, false));
        assertFalse(resolve(false, true, true, false, false, true, true, false, false));
        assertTrue(resolve(true, true, true, false, false, true, true, false, false));
    }

    private static boolean resolve(
            boolean vanillaCanHit,
            boolean playerWindCharge,
            boolean activePromotedWindSpiritOwner,
            boolean targetIsOwner,
            boolean playerTarget,
            boolean targetAlive,
            boolean targetParticipating,
            boolean targetSpectator,
            boolean targetActiveWraith
    ) {
        return WindSpiritRules.resolveWindChargeHit(
                vanillaCanHit,
                playerWindCharge,
                activePromotedWindSpiritOwner,
                targetIsOwner,
                playerTarget,
                targetAlive,
                targetParticipating,
                targetSpectator,
                targetActiveWraith
        );
    }
}
