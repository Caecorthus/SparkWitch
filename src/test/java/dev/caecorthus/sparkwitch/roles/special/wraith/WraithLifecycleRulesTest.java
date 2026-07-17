package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithLifecycleRulesTest {
    @Test
    void thirdCompletionQueuesPromotionAndIsolationIsBilateral() {
        assertFalse(WraithLifecycleRules.shouldQueuePromotion(true, false, 2));
        assertTrue(WraithLifecycleRules.shouldQueuePromotion(true, false, 3));
        assertFalse(WraithLifecycleRules.shouldQueuePromotion(true, true, 3));
        assertTrue(WraithLifecycleRules.canAffectPlayer(false, false, false));
        assertTrue(WraithLifecycleRules.canAffectPlayer(true, true, true));
        assertFalse(WraithLifecycleRules.canAffectPlayer(true, false, false));
        assertFalse(WraithLifecycleRules.canAffectPlayer(false, true, false));
    }

    @Test
    void onlyANewlyTriggeredLastStandCancelsThePendingDeath() {
        assertTrue(WraithLifecycleRules.didNewLastStandTrigger(false, true));
        assertFalse(WraithLifecycleRules.didNewLastStandTrigger(true, true));
        assertFalse(WraithLifecycleRules.didNewLastStandTrigger(false, false));
    }

    @Test
    void adminSpectatorAndReconnectRulesStayAuthoritative() {
        assertTrue(WraithLifecycleRules.shouldTerminateForFall(true, -0.01D, 0.0D));
        assertFalse(WraithLifecycleRules.shouldTerminateForFall(true, 0.0D, 0.0D));
        assertTrue(WraithLifecycleRules.shouldResume(true, true, true, true));
        assertFalse(WraithLifecycleRules.shouldResume(true, true, true, false));
    }
}
